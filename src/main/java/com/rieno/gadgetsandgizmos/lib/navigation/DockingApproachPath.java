package com.rieno.gadgetsandgizmos.lib.navigation;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.ToDoubleFunction;

// Retain a directed shared corridor in a connector's local frame
public record DockingApproachPath(double distance, List<Vec3> corridor, SplinePathNetwork network, List<Connection> connections, SplinePathNetwork laneLayout){
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final double DEFAULT_DISTANCE = 10.0D;
    public static final int MAX_POINTS = 256;
    public static final DockingApproachPath DEFAULT = new DockingApproachPath(DEFAULT_DISTANCE, List.of());
    private static final int MAX_CACHED_FRAMES = 16;
    private static final int MAX_CACHED_ROUTES = 16;
    private static final int MAX_CACHED_ROUTE_POINTS = 16_384;
    private static final ThreadLocal<LinkedHashMap<FrameKey, SplinePathNetwork>> WORLD_NETWORKS =
            ThreadLocal.withInitial(() -> new LinkedHashMap<>(MAX_CACHED_FRAMES, 0.75F, true));
    private static final ThreadLocal<LinkedHashMap<RouteKey, RouteSnapshot>> ROUTES =
            ThreadLocal.withInitial(() -> new LinkedHashMap<>(MAX_CACHED_ROUTES, 0.75F, true));
    private static final ThreadLocal<LinkedHashMap<CrossingKey, List<SplinePathNetwork.Snap>>> CROSSINGS =
            ThreadLocal.withInitial(() -> new LinkedHashMap<>(MAX_CACHED_FRAMES, 0.75F, true));

    // Store removable connector controls between the required alignment target and a main lane
    public record Connection(UUID junction, List<Vec3> controls){
        public Connection{
            if(junction == null || controls.size() > MAX_POINTS || controls.stream().anyMatch(point -> !finite(point)))
                throw new IllegalArgumentException("Invalid connector spline");
            controls = List.copyOf(controls);
        }
    }

    public DockingApproachPath(double distance, List<Vec3> corridor, SplinePathNetwork network){
        this(distance, corridor, network, List.of());
    }

    public DockingApproachPath(double distance, List<Vec3> corridor, SplinePathNetwork network, List<Connection> connections){
        this(distance, corridor, network, connections, SplinePathNetwork.EMPTY);
    }

    // Preserve the existing two-argument corridor API
    public DockingApproachPath(double distance, List<Vec3> corridor){
        this(distance, corridor, SplinePathNetwork.linear(corridor));
    }

    public DockingApproachPath{
        distance = Double.isFinite(distance) ? Math.max(1.0D, Math.min(DockingApproachAxis.MAX_DISTANCE, distance)) : DEFAULT_DISTANCE;
        network = network == null ? SplinePathNetwork.linear(corridor) : network;
        laneLayout = laneLayout == null ? SplinePathNetwork.EMPTY : laneLayout;
        if(!network.isEmpty()) corridor = network.points(network.paths().getFirst());
        corridor = corridor == null ? List.of() : corridor.stream().filter(DockingApproachPath::finite)
                .limit(MAX_POINTS).toList();
        corridor = WaypointSpline.of(corridor).waypoints();
        if(corridor.size() < 2) corridor = List.of();
        connections = connections == null ? List.of() : List.copyOf(connections);
        if(connections.size() > SplinePathNetwork.MAX_PATHS
                || connections.stream().mapToInt(connection -> connection.controls().size()).sum() > MAX_POINTS)
            throw new IllegalArgumentException("Too many connector controls");
        for(Connection connection : connections) if(network.node(connection.junction()) == null)
            throw new IllegalArgumentException("Missing connector junction");
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Project the alignment target onto the connector's outward axis
    public static Vec3 constrainTarget(Vec3 tip, Vec3 facing, Vec3 requested){
        Vec3 axis = facing.normalize();
        double distance = finite(requested) ? requested.subtract(tip).dot(axis) : DEFAULT_DISTANCE;
        return tip.add(axis.scale(Math.max(1.0D, Math.min(DockingApproachAxis.MAX_DISTANCE, distance))));
    }

    public boolean configured(){ return network.routable(); }
    public Vec3 target(Vec3 tip, Vec3 facing){ return tip.add(facing.normalize().scale(distance)); }

    // Capture the shared world corridor relative to this connector
    public static DockingApproachPath capture(Vec3 tip, Vec3 facing, Vec3 up, Vec3 target, List<Vec3> points){
        return capture(tip, facing, up, target, SplinePathNetwork.linear(points));
    }

    // Retain junction identities and endpoint roles in the connector's material frame
    public static DockingApproachPath capture(Vec3 tip, Vec3 facing, Vec3 up, Vec3 target, SplinePathNetwork network){
        return capture(tip, facing, up, target, network, List.of());
    }

    public static DockingApproachPath capture(Vec3 tip, Vec3 facing, Vec3 up, Vec3 target,
            SplinePathNetwork network, List<Connection> connections){
        Vec3 axis = facing.normalize();
        Vec3 vertical = vertical(axis, up);
        Vec3 side = axis.cross(vertical).normalize();
        SplinePathNetwork local = network.transformed(point -> {
            Vec3 offset = point.subtract(tip);
            return new Vec3(offset.dot(side), offset.dot(vertical), offset.dot(axis));
        });
        List<Connection> captured = connections.stream().map(connection -> new Connection(connection.junction(),
                connection.controls().stream().map(point -> {
                    Vec3 offset = point.subtract(tip);
                    return new Vec3(offset.dot(side), offset.dot(vertical), offset.dot(axis));
                }).toList())).toList();
        return new DockingApproachPath(constrainTarget(tip, axis, target).subtract(tip).dot(axis), List.of(), local, captured);
    }

    public SplinePathNetwork worldNetwork(Vec3 tip, Vec3 facing, Vec3 up){
        FrameKey key = new FrameKey(network, tip, facing, up);
        var frames = WORLD_NETWORKS.get();
        SplinePathNetwork cached = frames.get(key);
        if(cached != null) return cached;
        Vec3 axis = facing.normalize();
        Vec3 vertical = vertical(axis, up);
        Vec3 side = axis.cross(vertical).normalize();
        SplinePathNetwork world = network.transformed(point -> tip.add(side.scale(point.x)).add(vertical.scale(point.y)).add(axis.scale(point.z)));
        if(frames.size() >= MAX_CACHED_FRAMES) frames.remove(frames.keySet().iterator().next());
        frames.put(key, world);
        return world;
    }

    // Retain editable controls separately from the generated traffic merge curves
    public static DockingApproachPath captureLanes(Vec3 tip, Vec3 facing, Vec3 up, Vec3 target, SplinePathNetwork lanes){
        return captureLanes(tip, facing, up, target, lanes, DockingApproachLanes.connected(lanes, up));
    }

    public static DockingApproachPath captureLanes(Vec3 tip, Vec3 facing, Vec3 up, Vec3 target,
            SplinePathNetwork lanes, SplinePathNetwork traffic){
        var layout = capture(tip, facing, up, target, lanes);
        var connected = capture(tip, facing, up, target, traffic);
        return new DockingApproachPath(connected.distance(), connected.corridor(), connected.network(), List.of(), layout.network());
    }

    public SplinePathNetwork worldLaneLayout(Vec3 tip, Vec3 facing, Vec3 up){
        if(laneLayout.isEmpty()) return worldNetwork(tip, facing, up);
        Vec3 axis = facing.normalize(), vertical = vertical(axis, up), side = axis.cross(vertical).normalize();
        return laneLayout.transformed(point -> tip.add(side.scale(point.x)).add(vertical.scale(point.y)).add(axis.scale(point.z)));
    }

    public List<Vec3> worldCorridor(Vec3 tip, Vec3 facing, Vec3 up){
        Vec3 axis = facing.normalize();
        Vec3 vertical = vertical(axis, up);
        Vec3 side = axis.cross(vertical).normalize();
        return corridor.stream().map(point -> tip.add(side.scale(point.x))
                .add(vertical.scale(point.y)).add(axis.scale(point.z))).toList();
    }

    public List<Connection> worldConnections(Vec3 tip, Vec3 facing, Vec3 up){
        Vec3 axis = facing.normalize(), vertical = vertical(axis, up), side = axis.cross(vertical).normalize();
        return connections.stream().map(connection -> new Connection(connection.junction(), connection.controls().stream()
                .map(point -> tip.add(side.scale(point.x)).add(vertical.scale(point.y)).add(axis.scale(point.z))).toList())).toList();
    }

    // Keep the compatibility route API while selecting correctly labelled terminal paths
    public List<Vec3> route(Vec3 tip, Vec3 facing, Vec3 up, boolean departing){
        return route(tip, facing, up, departing, null, null);
    }

    public List<Vec3> route(Vec3 tip, Vec3 facing, Vec3 up, boolean departing,
            Vec3 externalTarget, ToDoubleFunction<List<Vec3>> trafficCost){
        return SplinePathNetwork.selectPrimary(routes(tip, facing, up, departing, trafficCost), externalTarget,
                trafficCost == null ? null : candidate -> trafficCost.applyAsDouble(candidate.controls()))
                .map(SplinePathNetwork.Route::controls).orElse(List.of());
    }

    // End ingress at an axis crossing; departure backs out on that axis before following the lane to its exit
    public List<SplinePathNetwork.Route> routes(Vec3 tip, Vec3 facing, Vec3 up, boolean departing){
        return routes(tip, facing, up, departing, null);
    }

    public List<SplinePathNetwork.Route> routes(Vec3 tip, Vec3 facing, Vec3 up, boolean departing, ToDoubleFunction<List<Vec3>> trafficCost){
        if(!configured()) return List.of();
        List<SplinePathNetwork.Route> routes = new ArrayList<>();
        SplinePathNetwork world = worldNetwork(tip, facing, up);
        RouteKey key = new RouteKey(world, tip, facing, distance, departing);
        if(trafficCost == null){
            RouteSnapshot cached = ROUTES.get().get(key);
            if(cached != null) return cached.routes();
        }
        for(var crossing : crossings(world, tip, facing)){
            List<SplinePathNetwork.Route> candidates = world.routesAt(crossing, departing, trafficCost);
            for(SplinePathNetwork.Route candidate : candidates){
                List<Vec3> controls = new ArrayList<>();
                if(departing){
                    controls.add(tip);
                    for(double distance = 0.5D; distance < crossing.position().distanceTo(tip); distance += 0.5D)
                        controls.add(tip.add(facing.normalize().scale(distance)));
                    controls.add(crossing.position());
                    controls.addAll(candidate.controls());
                }else{
                    controls.addAll(candidate.controls());
                }
                WaypointSpline spline = WaypointSpline.of(controls);
                List<Vec3> points = spline.waypoints();
                routes.add(new SplinePathNetwork.Route(candidate.endpointId(), candidate.endpoint(), points,
                        spline.length(), Math.max(candidate.priority(), DockingApproachLanes.laneIndex(world, crossing.pathId()))));
            }
        }
        List<SplinePathNetwork.Route> res = List.copyOf(routes);
        if(trafficCost == null) retainRoutes(key, res);
        return res;
    }

    // Retain immutable geometry only; traffic callbacks must run for every assessment
    private static void retainRoutes(RouteKey key, List<SplinePathNetwork.Route> routes){
        int points = routes.stream().mapToInt(route -> route.controls().size()).sum();
        if(points > MAX_CACHED_ROUTE_POINTS) return;
        var cached = ROUTES.get();
        int count = points;
        for(RouteSnapshot snapshot : cached.values()) count += snapshot.points();
        while(!cached.isEmpty() && (cached.size() >= MAX_CACHED_ROUTES || count > MAX_CACHED_ROUTE_POINTS)){
            RouteKey oldest = cached.keySet().iterator().next();
            count -= cached.remove(oldest).points();
        }
        cached.put(key, new RouteSnapshot(routes, points));
    }

    private record FrameKey(SplinePathNetwork network, Vec3 tip, Vec3 facing, Vec3 up){}
    private record RouteKey(SplinePathNetwork network, Vec3 tip, Vec3 facing, double distance, boolean departing){}
    private record RouteSnapshot(List<SplinePathNetwork.Route> routes, int points){}
    private record CrossingKey(SplinePathNetwork network, Vec3 tip, Vec3 facing, double distance){}

    // Crossings depend on connector geometry rather than live traffic
    private List<SplinePathNetwork.Snap> crossings(SplinePathNetwork world, Vec3 tip, Vec3 facing){
        CrossingKey key = new CrossingKey(world, tip, facing, distance);
        var cached = CROSSINGS.get();
        List<SplinePathNetwork.Snap> res = cached.get(key);
        if(res != null) return res;
        res = new DockingApproachAxis(tip, facing, distance).intersections(world);
        if(cached.size() >= MAX_CACHED_FRAMES) cached.remove(cached.keySet().iterator().next());
        cached.put(key, res);
        return res;
    }

    public CompoundTag toTag(){
        CompoundTag tag = new CompoundTag();
        tag.putDouble("Distance", distance);
        tag.putBoolean("DetectionAxis", true);
        tag.put("Corridor", WaypointSplineData.points(corridor));
        tag.put("Network", network.toTag());
        if(!laneLayout.isEmpty()) tag.put("Lanes", laneLayout.toTag());
        tag.put("Connections", connectionsTag(connections));
        return tag;
    }

    public static DockingApproachPath fromTag(CompoundTag tag){
        if(tag == null || !tag.contains("Distance")) return DEFAULT;
        ListTag stored = tag.getList("Corridor", Tag.TAG_COMPOUND);
        List<Vec3> corridor = WaypointSplineData.readPoints(stored, MAX_POINTS);
        SplinePathNetwork network = tag.contains("Network", Tag.TAG_COMPOUND) ? SplinePathNetwork.fromTag(tag.getCompound("Network")) : SplinePathNetwork.linear(corridor);
        double distance = tag.getDouble("Distance");
        if(!tag.getBoolean("DetectionAxis")){
            // Old distances described branch targets; cover existing lane crossings when upgrading those saves
            var axis = new DockingApproachAxis(Vec3.ZERO, new Vec3(0, 0, 1), DockingApproachAxis.MAX_DISTANCE);
            distance = Math.max(distance, axis.intersections(network).stream().mapToDouble(point -> point.position().z).max().orElse(distance));
        }
        return new DockingApproachPath(distance, corridor, network,
                readConnections(tag.getList("Connections", Tag.TAG_COMPOUND)),
                tag.contains("Lanes", Tag.TAG_COMPOUND) ? SplinePathNetwork.fromTag(tag.getCompound("Lanes")) : SplinePathNetwork.EMPTY);
    }

    public static ListTag connectionsTag(List<Connection> connections){
        ListTag res = new ListTag();
        for(Connection connection : connections){
            CompoundTag val = new CompoundTag();
            val.putUUID("Junction", connection.junction());
            val.put("Points", WaypointSplineData.points(connection.controls()));
            res.add(val);
        }
        return res;
    }

    public static List<Connection> readConnections(ListTag stored){
        if(stored.size() > SplinePathNetwork.MAX_PATHS) throw new IllegalArgumentException("Too many connector splines");
        List<Connection> res = new ArrayList<>();
        int count = 0;
        for(int idx = 0; idx < stored.size(); idx++){
            CompoundTag val = stored.getCompound(idx);
            ListTag points = val.getList("Points", Tag.TAG_COMPOUND);
            count += points.size();
            if(count > MAX_POINTS) throw new IllegalArgumentException("Too many connector controls");
            res.add(new Connection(val.getUUID("Junction"), WaypointSplineData.readPoints(points, MAX_POINTS)));
        }
        return List.copyOf(res);
    }

    private static Vec3 vertical(Vec3 facing, Vec3 up){
        Vec3 val = up.subtract(facing.scale(up.dot(facing)));
        return val.lengthSqr() > 1.0E-9D ? val.normalize() : new Vec3(0, 1, 0);
    }

    private static boolean finite(Vec3 val){
        return val != null && Double.isFinite(val.x) && Double.isFinite(val.y) && Double.isFinite(val.z);
    }
}
