package com.rieno.gadgetsandgizmos.lib.navigation;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToDoubleFunction;
import java.util.function.UnaryOperator;

// Retain shared spline junctions and select directed travel between authored entrances and exits
public final class SplinePathNetwork{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           DEFAULTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final int MAX_NODES = 256;
    public static final int MAX_PATHS = 64;
    public static final int MAX_CONTROLS = 2048;
    public static final double JOIN_RADIUS = 0.75D;
    public static final SplinePathNetwork EMPTY = new SplinePathNetwork(List.of(), List.of());
    private final List<Node> nodes;
    private final List<Path> paths;
    private final Map<UUID, Node> byId;
    private final Map<UUID, WaypointSpline> splines;
    private final int hash;
    private static final int MAX_PREPARED_JOINS = 4;
    private static final int MAX_PREPARED_POINTS = 16_384;
    private final Map<PreparedKey, PreparedRoutes> preparedRoutes = new LinkedHashMap<>(4, 0.75F, true);
    private final Map<List<Vec3>, Boolean> primaryLanes = new LinkedHashMap<>(16, 0.75F, true);
    private volatile Boolean cachedRoutable;

    public enum Role{ JUNCTION, ENTER, EXIT }
    public record Node(UUID id, Vec3 position, Role role){
        public Node{
            if(id == null || !finite(position) || role == null) throw new IllegalArgumentException("Invalid spline node");
        }
    }
    public record Path(UUID id, List<UUID> nodes){
        public Path{ nodes = List.copyOf(nodes); }
    }
    public record Snap(UUID pathId, int segment, Vec3 position, UUID nodeId){}
    public record Route(UUID endpointId, Vec3 endpoint, List<Vec3> controls, double length, int priority){
        public Route(UUID endpointId, Vec3 endpoint, List<Vec3> controls, double length){ this(endpointId, endpoint, controls, length, 0); }
        public Route{ controls = List.copyOf(controls); }
    }

    public SplinePathNetwork(List<Node> nodes, List<Path> paths){
        if(nodes.size() > MAX_NODES || paths.size() > MAX_PATHS) throw new IllegalArgumentException("Spline network exceeds its limit");
        this.nodes = List.copyOf(nodes);
        this.paths = List.copyOf(paths);
        Map<UUID, Node> indexed = new LinkedHashMap<>();
        for(Node node : nodes) if(indexed.putIfAbsent(node.id(), node) != null) throw new IllegalArgumentException("Duplicate spline node");
        Map<UUID, WaypointSpline> curves = new LinkedHashMap<>();
        int count = 0;
        for(Path path : paths){
            count += path.nodes().size();
            if(path.id() == null || path.nodes().size() < 2 || count > MAX_CONTROLS || curves.containsKey(path.id()))
                throw new IllegalArgumentException("Invalid spline path");
            List<Vec3> points = new ArrayList<>();
            for(UUID id : path.nodes()){
                Node node = indexed.get(id);
                if(node == null || !points.isEmpty() && points.getLast().distanceToSqr(node.position()) < 1.0E-12D)
                    throw new IllegalArgumentException("Invalid spline path control");
                points.add(node.position());
            }
            curves.put(path.id(), WaypointSpline.of(points));
        }
        byId = Map.copyOf(indexed);
        splines = Map.copyOf(curves);
        hash = Objects.hash(this.nodes, this.paths);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public List<Node> nodes(){ return nodes; }
    public List<Path> paths(){ return paths; }
    public Node node(UUID id){ return byId.get(id); }
    public boolean isEmpty(){ return paths.isEmpty(); }
    public WaypointSpline spline(UUID id){ return splines.get(id); }
    public List<Vec3> points(Path path){ return path.nodes().stream().map(id -> byId.get(id).position()).toList(); }

    // Migrate the existing corridor without changing its default travel direction
    public static SplinePathNetwork linear(List<Vec3> controls){
        List<Vec3> points = WaypointSpline.of(controls).waypoints().stream().limit(MAX_NODES).toList();
        if(points.size() < 2) return EMPTY;
        List<Node> nodes = new ArrayList<>();
        for(int idx = 0; idx < points.size(); idx++) nodes.add(new Node(new UUID(0, idx + 1), points.get(idx),
                idx == 0 ? Role.ENTER : idx == points.size() - 1 ? Role.EXIT : Role.JUNCTION));
        return new SplinePathNetwork(nodes, List.of(new Path(new UUID(0, 0), nodes.stream().map(Node::id).toList())));
    }

    public SplinePathNetwork transformed(UnaryOperator<Vec3> transform){
        return new SplinePathNetwork(nodes.stream().map(node -> new Node(node.id(), transform.apply(node.position()), node.role())).toList(), paths);
    }

    public int degree(UUID id){
        int count = 0;
        for(Path path : paths) for(int idx = 0; idx < path.nodes().size(); idx++) if(path.nodes().get(idx).equals(id)){
            if(idx > 0) count++;
            if(idx + 1 < path.nodes().size()) count++;
        }
        return count;
    }

    // Swap both free ends of a path while leaving shared junctions unchanged
    public SplinePathNetwork flipEndpoint(UUID id){
        Node selected = byId.get(id);
        if(selected == null || selected.role() == Role.JUNCTION) return this;
        Set<UUID> flipped = new HashSet<>();
        flipped.add(id);
        for(Path path : paths){
            UUID other = path.nodes().getFirst().equals(id) ? path.nodes().getLast()
                    : path.nodes().getLast().equals(id) ? path.nodes().getFirst() : null;
            if(other != null && byId.get(other).role() != Role.JUNCTION) flipped.add(other);
        }
        return new SplinePathNetwork(nodes.stream().map(node -> !flipped.contains(node.id()) ? node
                : new Node(node.id(), node.position(), node.role() == Role.ENTER ? Role.EXIT : Role.ENTER)).toList(), paths);
    }

    public SplinePathNetwork move(UUID id, Vec3 position){
        if(!byId.containsKey(id) || !finite(position)) return this;
        return new SplinePathNetwork(nodes.stream().map(node -> node.id().equals(id)
                ? new Node(id, position, node.role()) : node).toList(), paths);
    }

    public SplinePathNetwork insert(UUID pathId, int segment, Vec3 position){
        Path path = path(pathId);
        if(path == null || segment < 0 || segment + 1 >= path.nodes().size() || nodes.size() >= MAX_NODES) return this;
        UUID id = UUID.randomUUID();
        List<Node> updated = new ArrayList<>(nodes);
        updated.add(new Node(id, position, Role.JUNCTION));
        List<UUID> controls = new ArrayList<>(path.nodes());
        controls.add(segment + 1, id);
        return replacePath(updated, new Path(pathId, controls));
    }

    // Keep the source control fixed while extending a new free entrance or exit
    public SplinePathNetwork branch(UUID source, Vec3 position, Role role){
        if(!byId.containsKey(source) || role == Role.JUNCTION || nodes.size() >= MAX_NODES || paths.size() >= MAX_PATHS
                || !finite(position) || byId.get(source).position().distanceToSqr(position) < 0.01D) return this;
        UUID endpoint = UUID.randomUUID();
        List<Node> updated = new ArrayList<>(nodes.stream().map(node -> node.id().equals(source)
                ? new Node(node.id(), node.position(), Role.JUNCTION) : node).toList());
        updated.add(new Node(endpoint, position, role));
        List<Path> branches = new ArrayList<>(paths);
        branches.add(new Path(UUID.randomUUID(), List.of(source, endpoint)));
        return new SplinePathNetwork(updated, branches).joinEndpoint(endpoint, position, JOIN_RADIUS);
    }

    // Snap only to another path so dragging a free end does not collapse its own curve
    public Optional<Snap> snap(Vec3 position, UUID movingNode, double radius){
        Snap best = null;
        double distance = radius * radius;
        for(Path path : paths){
            if(movingNode != null && path.nodes().contains(movingNode)) continue;
            WaypointSpline.Projection projection = splines.get(path.id()).project(position);
            if(!projection.found() || projection.position().distanceToSqr(position) > distance) continue;
            int idx = projection.segmentIndex();
            UUID nodeId = null;
            if(projection.position().distanceToSqr(byId.get(path.nodes().get(idx)).position()) < 0.0625D) nodeId = path.nodes().get(idx);
            else if(projection.position().distanceToSqr(byId.get(path.nodes().get(idx + 1)).position()) < 0.0625D) nodeId = path.nodes().get(idx + 1);
            Vec3 snapped = nodeId == null ? projection.position() : byId.get(nodeId).position();
            if(snapped.distanceToSqr(position) > distance) continue;
            distance = snapped.distanceToSqr(position);
            best = new Snap(path.id(), idx, snapped, nodeId);
        }
        return Optional.ofNullable(best);
    }

    // Reuse an existing control or split the destination curve at an explicit shared junction
    public SplinePathNetwork joinEndpoint(UUID id, Vec3 position, double radius){
        Node endpoint = byId.get(id);
        if(endpoint == null || endpoint.role() == Role.JUNCTION || degree(id) != 1) return move(id, position);
        Optional<Snap> selected = snap(position, id, radius);
        if(selected.isEmpty()) return move(id, position);
        Snap snap = selected.get();
        UUID junction = snap.nodeId() == null ? UUID.randomUUID() : snap.nodeId();
        List<Node> updated = new ArrayList<>();
        for(Node node : nodes){
            if(node.id().equals(id)) continue;
            updated.add(node.id().equals(junction) ? new Node(junction, snap.position(), Role.JUNCTION) : node);
        }
        if(snap.nodeId() == null) updated.add(new Node(junction, snap.position(), Role.JUNCTION));
        List<Path> joined = new ArrayList<>();
        for(Path path : paths){
            List<UUID> controls = new ArrayList<>(path.nodes());
            if(path.id().equals(snap.pathId()) && snap.nodeId() == null) controls.add(snap.segment() + 1, junction);
            Collections.replaceAll(controls, id, junction);
            joined.add(new Path(path.id(), controls));
        }
        return new SplinePathNetwork(updated, joined);
    }

    public SplinePathNetwork remove(UUID id){
        if(!byId.containsKey(id) || degree(id) != 2) return this;
        List<Path> updated = new ArrayList<>();
        for(Path path : paths){
            if(!path.nodes().contains(id)){ updated.add(path); continue; }
            if(path.nodes().getFirst().equals(id) || path.nodes().getLast().equals(id) || path.nodes().size() <= 2) return this;
            updated.add(new Path(path.id(), path.nodes().stream().filter(node -> !node.equals(id)).toList()));
        }
        return new SplinePathNetwork(nodes.stream().filter(node -> !node.id().equals(id)).toList(), updated);
    }

    // Copy a complete main lane without changing its source controls or terminal roles
    public SplinePathNetwork duplicate(UUID pathId, Vec3 offset){
        Path source = path(pathId);
        if(source == null || !finite(offset) || offset.lengthSqr() < 0.01D
                || nodes.size() + source.nodes().size() > MAX_NODES || paths.size() >= MAX_PATHS
                || byId.get(source.nodes().getFirst()).role() == Role.JUNCTION
                || byId.get(source.nodes().getLast()).role() == Role.JUNCTION) return this;
        List<Node> updated = new ArrayList<>(nodes);
        List<UUID> controls = new ArrayList<>();
        for(UUID id : source.nodes()){
            Node node = byId.get(id);
            UUID copy = UUID.randomUUID();
            controls.add(copy);
            updated.add(new Node(copy, node.position().add(offset), node.role()));
        }
        List<Path> copies = new ArrayList<>(paths);
        copies.add(new Path(UUID.randomUUID(), controls));
        return new SplinePathNetwork(updated, copies);
    }

    // Preserve the required main lane while allowing added lanes and branches to be removed
    public boolean canRemoveEndpoint(UUID id, UUID requiredPath){
        Node node = byId.get(id);
        if(node == null || node.role() == Role.JUNCTION || degree(id) != 1) return false;
        return paths.stream().anyMatch(path -> !path.id().equals(requiredPath) && path.nodes().contains(id));
    }

    public SplinePathNetwork removeEndpoint(UUID id, UUID requiredPath){
        if(!canRemoveEndpoint(id, requiredPath)) return this;
        List<Path> retained = paths.stream().filter(path -> !path.nodes().contains(id)).toList();
        Set<UUID> used = new HashSet<>();
        retained.forEach(path -> used.addAll(path.nodes()));
        return new SplinePathNetwork(nodes.stream().filter(node -> used.contains(node.id())).toList(), retained);
    }

    // Every separate main lane needs its own entrance and exit
    public boolean routable(){
        Boolean cached = cachedRoutable;
        if(cached != null) return cached;
        boolean res = checkRoutable();
        cachedRoutable = res;
        return res;
    }

    // Endpoint roles and connectivity are immutable for this network
    private boolean checkRoutable(){
        if(paths.isEmpty() || nodes.stream().noneMatch(node -> node.role() == Role.ENTER)
                || nodes.stream().noneMatch(node -> node.role() == Role.EXIT)) return false;
        Map<UUID, Set<UUID>> neighbors = new HashMap<>();
        for(Path path : paths) for(int idx = 1; idx < path.nodes().size(); idx++){
            UUID from = path.nodes().get(idx - 1), to = path.nodes().get(idx);
            neighbors.computeIfAbsent(from, key -> new HashSet<>()).add(to);
            neighbors.computeIfAbsent(to, key -> new HashSet<>()).add(from);
        }
        Set<UUID> reached = new HashSet<>();
        ArrayDeque<UUID> pending = new ArrayDeque<>();
        for(Node start : nodes){
            if(reached.contains(start.id())) continue;
            boolean enter = false, exit = false;
            pending.add(start.id());
            while(!pending.isEmpty()){
                UUID id = pending.removeFirst();
                if(!reached.add(id)) continue;
                enter |= byId.get(id).role() == Role.ENTER;
                exit |= byId.get(id).role() == Role.EXIT;
                pending.addAll(neighbors.getOrDefault(id, Set.of()));
            }
            if(!enter || !exit) return false;
        }
        return reached.size() == nodes.size() && nodes.stream().allMatch(node -> node.role() == Role.JUNCTION || degree(node.id()) == 1);
    }

    // Sample each cubic leg in order and search all correctly labelled terminal routes
    public List<Route> routes(Vec3 target, boolean departing){ return routes(target, departing, null); }

    public List<Route> routes(Vec3 target, boolean departing, ToDoubleFunction<List<Vec3>> trafficCost){
        if(!routable()) return List.of();
        Snap join = nearest(target);
        return routes(join, departing, trafficCost);
    }

    // Use an authored attachment identity even where separate lanes cross
    public List<Route> routes(UUID junction, boolean departing, ToDoubleFunction<List<Vec3>> trafficCost){
        if(!routable() || !byId.containsKey(junction)) return List.of();
        for(Path path : paths){
            int idx = path.nodes().indexOf(junction);
            if(idx >= 0) return routes(new Snap(path.id(), Math.min(idx, path.nodes().size() - 2),
                    byId.get(junction).position(), junction), departing, trafficCost);
        }
        return List.of();
    }

    // Route through an intersection on a specific lane without joining separate curves
    public List<Route> routesAt(Snap crossing, boolean departing, ToDoubleFunction<List<Vec3>> trafficCost){
        Path path = crossing == null ? null : path(crossing.pathId());
        if(!routable() || path == null || crossing.segment() < 0 || crossing.segment() + 1 >= path.nodes().size()) return List.of();
        if(crossing.nodeId() != null && !path.nodes().contains(crossing.nodeId())) return List.of();
        return routes(crossing, departing, trafficCost);
    }

    private List<Route> routes(Snap join, boolean departing, ToDoubleFunction<List<Vec3>> trafficCost){
        if(join == null) return List.of();
        PreparedRoutes prepared = prepareRoutes(join, departing);
        int anchor = prepared.anchor;
        List<List<Arc>> graph = prepared.graph;
        double[] distances = new double[nodes.size() + 1];
        java.util.Arrays.fill(distances, Double.POSITIVE_INFINITY);
        Arc[] prev = new Arc[nodes.size() + 1];
        distances[anchor] = 0.0D;
        PriorityQueue<Visit> queue = new PriorityQueue<>(Comparator.comparingDouble(Visit::distance).thenComparingInt(Visit::node));
        queue.add(new Visit(anchor, 0.0D));
        while(!queue.isEmpty()){
            Visit visit = queue.remove();
            if(visit.distance() > distances[visit.node()] + 1.0E-9D) continue;
            if(visit.node() != anchor && nodes.get(visit.node()).role() != Role.JUNCTION) continue;
            for(Arc arc : graph.get(visit.node())){
                double delay = 0.0D;
                if(trafficCost != null && arc.length() > 1.0E-9D){
                    delay = safeCost(trafficCost.applyAsDouble(new ArrayList<>(prepared.controls(arc))));
                }
                double distance = visit.distance() + arc.length() + delay;
                if(distance + 1.0E-9D >= distances[arc.to()]) continue;
                distances[arc.to()] = distance;
                prev[arc.to()] = arc;
                queue.add(new Visit(arc.to(), distance));
            }
        }
        List<Route> routes = new ArrayList<>();
        for(int idx = 0; idx < nodes.size(); idx++){
            Node endpoint = nodes.get(idx);
            if(endpoint.role() != (departing ? Role.EXIT : Role.ENTER) || !Double.isFinite(distances[idx])) continue;
            List<Arc> arcs = new ArrayList<>();
            int cursor = idx;
            while(cursor != anchor && prev[cursor] != null){ arcs.add(prev[cursor]); cursor = prev[cursor].from(); }
            if(cursor != anchor) continue;
            Collections.reverse(arcs);
            RouteGeometry geometry = prepared.geometry(arcs, join.position(), departing, this);
            routes.add(new Route(endpoint.id(), endpoint.position(), geometry.controls(), geometry.length(), geometry.priority()));
        }
        routes.sort(Comparator.comparingDouble(Route::length).thenComparing(Route::endpointId));
        return List.copyOf(routes);
    }

    // Reuse directed edges while evaluating live traffic costs for every route request
    private synchronized PreparedRoutes prepareRoutes(Snap join, boolean departing){
        PreparedKey key = new PreparedKey(join, departing);
        PreparedRoutes cached = preparedRoutes.get(key);
        if(cached != null) return cached;
        Path selected = path(join.pathId());
        WaypointSpline.Segment joinedSegment = splines.get(join.pathId()).segments().get(join.segment());
        double fraction = joinedSegment.nearestFraction(join.position(), 0.0D);
        Map<UUID, Integer> indices = new HashMap<>();
        for(int idx = 0; idx < nodes.size(); idx++) indices.put(nodes.get(idx).id(), idx);
        int anchor = join.nodeId() != null ? indices.get(join.nodeId())
                : fraction <= 1.0E-8D ? indices.get(selected.nodes().get(join.segment()))
                : fraction >= 1.0D - 1.0E-8D ? indices.get(selected.nodes().get(join.segment() + 1)) : nodes.size();
        Map<UUID, Boolean> directions = flowDirections(indices);
        List<List<Arc>> graph = new ArrayList<>();
        for(int idx = 0; idx <= nodes.size(); idx++) graph.add(new ArrayList<>());
        for(Path path : paths){
            boolean forward = directions.get(path.id()) == departing;
            WaypointSpline spline = splines.get(path.id());
            for(int idx = 0; idx < spline.segments().size(); idx++){
                int from = indices.get(path.nodes().get(idx)), to = indices.get(path.nodes().get(idx + 1));
                WaypointSpline.Segment segment = spline.segments().get(idx);
                if(anchor == nodes.size() && path.id().equals(selected.id()) && idx == join.segment()){
                    connectDirected(graph, from, anchor, segment, 0.0D, fraction, forward);
                    connectDirected(graph, anchor, to, segment, fraction, 1.0D, forward);
                }else connectDirected(graph, from, to, segment, 0.0D, 1.0D, forward);
            }
        }
        PreparedRoutes res = new PreparedRoutes(anchor, graph.stream().map(List::copyOf).toList());
        if(preparedRoutes.size() >= MAX_PREPARED_JOINS) preparedRoutes.remove(preparedRoutes.keySet().iterator().next());
        preparedRoutes.put(key, res);
        return res;
    }

    // Classify immutable route controls without calculating unused projection details
    public synchronized boolean followsPrimaryLane(List<Vec3> controls){
        if(isEmpty() || controls.isEmpty()) return true;
        Boolean cached = primaryLanes.get(controls);
        if(cached != null) return cached;
        WaypointSpline primary = splines.get(paths.getFirst().id());
        boolean res = controls.stream().allMatch(point -> primary.distanceTo(point) < 0.5D);
        if(controls.size() <= MAX_PREPARED_POINTS){
            int points = controls.size();
            for(List<Vec3> key : primaryLanes.keySet()) points += key.size();
            while(!primaryLanes.isEmpty() && (primaryLanes.size() >= 128 || points > MAX_PREPARED_POINTS)){
                List<Vec3> oldest = primaryLanes.keySet().iterator().next();
                points -= oldest.size();
                primaryLanes.remove(oldest);
            }
            primaryLanes.put(Collections.unmodifiableList(new ArrayList<>(controls)), res);
        }
        return res;
    }

    // Include external travel and caller-supplied traffic cost without depending on a vehicle implementation
    public static Optional<Route> select(List<Route> routes, Vec3 externalTarget, ToDoubleFunction<Route> trafficCost){
        return routes.stream().min(Comparator.comparingDouble((Route route) -> route.length()
                + (externalTarget == null ? 0.0D : externalTarget.distanceTo(route.endpoint()))
                + (trafficCost == null ? 0.0D : safeCost(trafficCost.applyAsDouble(route))))
                .thenComparing(Route::endpointId));
    }

    // Use the primary lane unless traffic or physical clearance requires another path
    public static Optional<Route> selectPrimary(List<Route> routes, Vec3 externalTarget, ToDoubleFunction<Route> trafficCost){
        return routes.stream().min(Comparator.comparingInt((Route route) -> trafficCost != null
                && trafficCost.applyAsDouble(route) > 0 ? 1 : 0).thenComparingInt(Route::priority)
                .thenComparingDouble(route -> route.length() + (externalTarget == null ? 0 : externalTarget.distanceTo(route.endpoint()))
                        + (trafficCost == null ? 0 : safeCost(trafficCost.applyAsDouble(route))))
                .thenComparing(Route::endpointId));
    }

    // Encode junction references as bounded indices to fit the serverbound payload limit
    public void write(FriendlyByteBuf buf){
        buf.writeVarInt(nodes.size());
        Map<UUID, Integer> indices = new HashMap<>();
        for(int idx = 0; idx < nodes.size(); idx++){
            Node node = nodes.get(idx);
            indices.put(node.id(), idx);
            buf.writeUUID(node.id());
            buf.writeDouble(node.position().x); buf.writeDouble(node.position().y); buf.writeDouble(node.position().z);
            buf.writeEnum(node.role());
        }
        buf.writeVarInt(paths.size());
        for(Path path : paths){
            buf.writeUUID(path.id());
            buf.writeVarInt(path.nodes().size());
            for(UUID id : path.nodes()) buf.writeVarInt(indices.get(id));
        }
    }

    public static SplinePathNetwork read(FriendlyByteBuf buf){
        int count = checkedCount(buf.readVarInt(), MAX_NODES);
        List<Node> nodes = new ArrayList<>();
        for(int idx = 0; idx < count; idx++) nodes.add(new Node(buf.readUUID(),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readEnum(Role.class)));
        int paths = checkedCount(buf.readVarInt(), MAX_PATHS);
        int controls = 0;
        List<Path> routes = new ArrayList<>();
        for(int idx = 0; idx < paths; idx++){
            UUID id = buf.readUUID();
            int size = checkedCount(buf.readVarInt(), MAX_CONTROLS - controls);
            controls += size;
            List<UUID> refs = new ArrayList<>();
            for(int point = 0; point < size; point++){
                int node = buf.readVarInt();
                if(node < 0 || node >= nodes.size()) throw new IllegalArgumentException("Invalid spline node reference");
                refs.add(nodes.get(node).id());
            }
            routes.add(new Path(id, refs));
        }
        return new SplinePathNetwork(nodes, routes);
    }

    public CompoundTag toTag(){
        CompoundTag tag = new CompoundTag();
        ListTag storedNodes = new ListTag();
        for(Node node : nodes){
            CompoundTag val = WaypointSplineData.vector(node.position());
            val.putUUID("Id", node.id());
            val.putString("Role", node.role().name());
            storedNodes.add(val);
        }
        tag.put("Nodes", storedNodes);
        ListTag storedPaths = new ListTag();
        for(Path path : paths){
            CompoundTag val = new CompoundTag();
            val.putUUID("Id", path.id());
            ListTag controls = new ListTag();
            for(UUID id : path.nodes()){
                CompoundTag ref = new CompoundTag(); ref.putUUID("Id", id); controls.add(ref);
            }
            val.put("Controls", controls); storedPaths.add(val);
        }
        tag.put("Paths", storedPaths);
        return tag;
    }

    // Read bounded data strictly so a malformed confirmation cannot be silently truncated
    public static SplinePathNetwork fromTag(CompoundTag tag){
        ListTag storedNodes = tag.getList("Nodes", Tag.TAG_COMPOUND), storedPaths = tag.getList("Paths", Tag.TAG_COMPOUND);
        if(storedNodes.size() > MAX_NODES || storedPaths.size() > MAX_PATHS) throw new IllegalArgumentException("Spline network exceeds its limit");
        List<Node> nodes = new ArrayList<>();
        for(int idx = 0; idx < storedNodes.size(); idx++){
            CompoundTag val = storedNodes.getCompound(idx);
            nodes.add(new Node(val.getUUID("Id"), WaypointSplineData.vector(val), Role.valueOf(val.getString("Role"))));
        }
        List<Path> paths = new ArrayList<>();
        int count = 0;
        for(int idx = 0; idx < storedPaths.size(); idx++){
            CompoundTag val = storedPaths.getCompound(idx);
            ListTag controls = val.getList("Controls", Tag.TAG_COMPOUND);
            count += controls.size();
            if(count > MAX_CONTROLS) throw new IllegalArgumentException("Spline controls exceed their limit");
            List<UUID> ids = new ArrayList<>();
            for(int point = 0; point < controls.size(); point++) ids.add(controls.getCompound(point).getUUID("Id"));
            paths.add(new Path(val.getUUID("Id"), ids));
        }
        return new SplinePathNetwork(nodes, paths);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private Path path(UUID id){ return paths.stream().filter(path -> path.id().equals(id)).findFirst().orElse(null); }
    private SplinePathNetwork replacePath(List<Node> updated, Path replacement){
        return new SplinePathNetwork(updated, paths.stream().map(path -> path.id().equals(replacement.id()) ? replacement : path).toList());
    }
    // Project precisely for route alignment; editing snaps separately reuse nearby authored controls
    public Optional<Snap> projection(Vec3 target){
        Snap best = null;
        double distance = Double.POSITIVE_INFINITY;
        for(Path path : paths){
            WaypointSpline.Projection projection = splines.get(path.id()).project(target);
            if(!projection.found() || projection.distance() >= distance) continue;
            distance = projection.distance();
            best = new Snap(path.id(), projection.segmentIndex(), projection.position(), null);
        }
        return Optional.ofNullable(best);
    }
    private Snap nearest(Vec3 target){ return projection(target).orElse(null); }
    private static void connect(List<List<Arc>> graph, int from, int to, WaypointSpline.Segment segment, double start, double end){
        double length = Math.max(0.0D, segment.lengthToFraction(end) - segment.lengthToFraction(start));
        graph.get(from).add(new Arc(from, to, segment, start, end, length));
        graph.get(to).add(new Arc(to, from, segment, end, start, length));
    }

    // Endpoint labels determine lane direction; joined detours inherit progress between those labels
    private Map<UUID, Boolean> flowDirections(Map<UUID, Integer> indices){
        List<List<Arc>> graph = new ArrayList<>();
        for(int idx = 0; idx < nodes.size(); idx++) graph.add(new ArrayList<>());
        for(Path path : paths) for(int idx = 0; idx + 1 < path.nodes().size(); idx++)
            connect(graph, indices.get(path.nodes().get(idx)), indices.get(path.nodes().get(idx + 1)),
                    splines.get(path.id()).segments().get(idx), 0, 1);
        double[] entries = terminalDistances(graph, Role.ENTER), exits = terminalDistances(graph, Role.EXIT);
        Map<UUID, Boolean> res = new HashMap<>();
        for(Path path : paths){
            Node first = byId.get(path.nodes().getFirst()), last = byId.get(path.nodes().getLast());
            boolean forward;
            if(first.role() == Role.ENTER || last.role() == Role.EXIT) forward = true;
            else if(first.role() == Role.EXIT || last.role() == Role.ENTER) forward = false;
            else{
                int from = indices.get(first.id()), to = indices.get(last.id());
                double delta = (entries[to] - exits[to]) - (entries[from] - exits[from]);
                forward = delta >= -1.0E-8D;
            }
            res.put(path.id(), forward);
        }
        return res;
    }

    private double[] terminalDistances(List<List<Arc>> graph, Role role){
        double[] res = new double[nodes.size()];
        java.util.Arrays.fill(res, Double.POSITIVE_INFINITY);
        PriorityQueue<Visit> queue = new PriorityQueue<>(Comparator.comparingDouble(Visit::distance));
        for(int idx = 0; idx < nodes.size(); idx++) if(nodes.get(idx).role() == role){
            res[idx] = 0;
            queue.add(new Visit(idx, 0));
        }
        while(!queue.isEmpty()){
            Visit visit = queue.remove();
            if(visit.distance() > res[visit.node()]) continue;
            for(Arc arc : graph.get(visit.node())){
                double distance = visit.distance() + arc.length();
                if(distance >= res[arc.to()]) continue;
                res[arc.to()] = distance;
                queue.add(new Visit(arc.to(), distance));
            }
        }
        return res;
    }

    private static void connectDirected(List<List<Arc>> graph, int from, int to, WaypointSpline.Segment segment,
            double start, double end, boolean forward){
        double length = Math.max(0, segment.lengthToFraction(end) - segment.lengthToFraction(start));
        if(forward) graph.get(from).add(new Arc(from, to, segment, start, end, length));
        else graph.get(to).add(new Arc(to, from, segment, end, start, length));
    }
    private static void append(List<Vec3> points, Arc arc){
        int samples = Math.max(1, (int)Math.ceil(arc.length() / 0.5D));
        for(int idx = 1; idx <= samples; idx++) points.add(arc.segment().pointAtFraction(arc.start() + (arc.end() - arc.start()) * idx / samples));
    }
    private static int checkedCount(int count, int maximum){
        if(count < 0 || count > maximum) throw new IllegalArgumentException("Spline network exceeds its limit");
        return count;
    }
    private static double safeCost(double cost){ return Double.isFinite(cost) ? Math.max(0.0D, cost) : 1.0E12D; }
    private static boolean finite(Vec3 val){ return val != null && Double.isFinite(val.x) && Double.isFinite(val.y) && Double.isFinite(val.z); }
    private record Arc(int from, int to, WaypointSpline.Segment segment, double start, double end, double length){}
    private record Visit(int node, double distance){}
    private record PreparedKey(Snap join, boolean departing){}
    private record RouteGeometry(List<Vec3> controls, double length, int priority){}

    // Bound retained samples independently from live obstacle and reservation costs
    private static final class PreparedRoutes{
        private final int anchor;
        private final List<List<Arc>> graph;
        private final Map<Arc, List<Vec3>> legs = new HashMap<>();
        private final Map<List<Arc>, RouteGeometry> routes = new LinkedHashMap<>(8, 0.75F, true);
        private int legPoints;
        private int routePoints;

        private PreparedRoutes(int anchor, List<List<Arc>> graph){ this.anchor = anchor; this.graph = graph; }

        private synchronized List<Vec3> controls(Arc arc){
            List<Vec3> cached = legs.get(arc);
            if(cached != null) return cached;
            List<Vec3> points = new ArrayList<>();
            points.add(arc.segment().pointAtFraction(arc.start()));
            append(points, arc);
            List<Vec3> res = List.copyOf(points);
            if(res.size() <= MAX_PREPARED_POINTS - legPoints){
                legs.put(arc, res);
                legPoints += res.size();
            }
            return res;
        }

        private synchronized RouteGeometry geometry(List<Arc> arcs, Vec3 join, boolean departing, SplinePathNetwork network){
            RouteGeometry cached = routes.get(arcs);
            if(cached != null) return cached;
            List<Vec3> controls = new ArrayList<>();
            controls.add(join);
            for(Arc arc : arcs){
                List<Vec3> leg = controls(arc);
                controls.addAll(leg.subList(1, leg.size()));
            }
            if(!departing) Collections.reverse(controls);
            controls = WaypointSpline.of(controls).waypoints();
            RouteGeometry res = new RouteGeometry(controls, arcs.stream().mapToDouble(Arc::length).sum(),
                    network.followsPrimaryLane(controls) ? 0 : 1);
            if(controls.size() <= MAX_PREPARED_POINTS){
                while(!routes.isEmpty() && (routes.size() >= 8 || routePoints + controls.size() > MAX_PREPARED_POINTS)){
                    List<Arc> oldest = routes.keySet().iterator().next();
                    routePoints -= routes.remove(oldest).controls().size();
                }
                routes.put(List.copyOf(arcs), res);
                routePoints += controls.size();
            }
            return res;
        }
    }
    @Override public boolean equals(Object val){ return val instanceof SplinePathNetwork other && nodes.equals(other.nodes) && paths.equals(other.paths); }
    @Override public int hashCode(){ return hash; }
}
