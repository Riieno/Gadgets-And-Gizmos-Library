package com.rieno.gadgetsandgizmos.lib.navigation;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import com.simibubi.create.content.trains.track.BezierConnection;
import net.createmod.catnip.data.Couple;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Edit corresponding lane controls and generate directed splits and merges outside the berth row
public final class DockingApproachLanes{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           DEFAULTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final double DEFAULT_SPACING = 12;
    public static final int DEFAULT_LANES = 2;
    private DockingApproachLanes(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Build the primary track from the connector tips and place the bypass farther outward
    public static SplinePathNetwork defaults(List<DockingApproachAxis> axes, Vec3 up){
        if(axes.isEmpty()) return SplinePathNetwork.EMPTY;
        Vec3 facing = axes.getFirst().facing(), side = facing.cross(up).normalize();
        if(side.lengthSqr() < 0.5D) throw new IllegalArgumentException("Invalid approach frame");
        List<Vec3> points = new ArrayList<>(axes.stream().map(axis -> axis.tip().add(axis.facing().scale(
                Math.min(axis.distance(), DockingApproachPath.DEFAULT_DISTANCE))))
                .sorted(Comparator.comparingDouble(point -> point.dot(side))).distinct().toList());
        points.addFirst(points.getFirst().subtract(side.scale(32)));
        points.add(points.getLast().add(side.scale(32)));
        return resize(SplinePathNetwork.linear(points), DEFAULT_LANES, facing);
    }

    // Upgrade detached lanes to matching controls while retaining their order and endpoint direction
    public static SplinePathNetwork prepare(SplinePathNetwork saved, List<DockingApproachAxis> axes, Vec3 up){
        if(saved.isEmpty()) return defaults(axes, up);
        var primary = saved.paths().getFirst();
        Set<UUID> ids = new HashSet<>(primary.nodes());
        var bound = DockingApproachAxis.bind(new SplinePathNetwork(saved.nodes().stream()
                .filter(node -> ids.contains(node.id())).toList(), List.of(primary)), axes).network();
        if(bound.paths().getFirst().nodes().equals(primary.nodes()) && structured(saved)) return trimEnds(resize(saved, saved.paths().size(), axes.getFirst().facing()));
        List<SplinePathNetwork.Node> nodes = new ArrayList<>(bound.nodes());
        List<SplinePathNetwork.Path> paths = new ArrayList<>(bound.paths());
        Vec3 facing = axes.getFirst().facing();
        for(int lane = 1; lane < saved.paths().size(); lane++){
            if(nodes.size() + bound.nodes().size() > SplinePathNetwork.MAX_NODES) break;
            var path = saved.paths().get(lane);
            List<UUID> controls = new ArrayList<>();
            for(UUID id : bound.paths().getFirst().nodes()){
                var node = bound.node(id);
                Vec3 position = saved.spline(path.id()).project(node.position()).position();
                double offset = position.subtract(node.position()).dot(facing);
                UUID copy = UUID.randomUUID();
                controls.add(copy);
                nodes.add(new SplinePathNetwork.Node(copy, node.position().add(facing.scale(offset)), node.role()));
            }
            paths.add(new SplinePathNetwork.Path(path.id(), controls));
        }
        var prepared = new SplinePathNetwork(nodes, paths);
        return trimEnds(resize(prepared, prepared.paths().size(), facing));
    }

    // Confirmed editable layouts retain their freeform controls when reopened
    public static SplinePathNetwork prepare(SplinePathNetwork saved, List<DockingApproachAxis> axes, Vec3 up, boolean authored){
        return authored && structured(saved) ? resize(saved, saved.paths().size(), axes.getFirst().facing()) : prepare(saved, axes, up);
    }

    public static boolean structured(SplinePathNetwork network){
        if(network.isEmpty()) return false;
        int count = network.paths().getFirst().nodes().size();
        var firstRole = network.node(network.paths().getFirst().nodes().getFirst()).role();
        Set<UUID> used = new HashSet<>();
        for(var path : network.paths()){
            if(path.nodes().size() != count) return false;
            for(UUID id : path.nodes()) if(!used.add(id)) return false;
            if(network.node(path.nodes().getFirst()).role() != firstRole || firstRole == SplinePathNetwork.Role.JUNCTION
                    || network.node(path.nodes().getLast()).role() != (firstRole == SplinePathNetwork.Role.ENTER
                    ? SplinePathNetwork.Role.EXIT : SplinePathNetwork.Role.ENTER)) return false;
            for(int idx = 1; idx < count - 1; idx++) if(network.node(path.nodes().get(idx)).role() != SplinePathNetwork.Role.JUNCTION) return false;
        }
        return used.size() == network.nodes().size();
    }

    public static int laneIndex(SplinePathNetwork network, UUID path){
        for(int idx = 0; idx < network.paths().size(); idx++) if(network.paths().get(idx).id().equals(path)) return idx;
        return -1;
    }

    // A point drag follows the connector axis and carries the same column on all outer lanes
    public static SplinePathNetwork movePoint(SplinePathNetwork network, int lane, int idx, Vec3 requested, Vec3 facing){
        if(!valid(network, lane, idx)) return network;
        Vec3 original = network.node(network.paths().get(lane).nodes().get(idx)).position();
        Vec3 target = constrainPoint(original, facing, requested);
        Vec3 offset = target.subtract(original);
        Map<UUID, Vec3> moved = new HashMap<>();
        for(int next = lane; next < network.paths().size(); next++){
            UUID id = network.paths().get(next).nodes().get(idx);
            moved.put(id, network.node(id).position().add(offset));
        }
        return move(network, moved);
    }

    public static Vec3 constrainPoint(Vec3 original, Vec3 facing, Vec3 requested){
        Vec3 axis = facing.normalize();
        double offset = requested.subtract(original).dot(axis);
        return Double.isFinite(offset) ? original.add(axis.scale(offset)) : original;
    }

    // Only existing sightline crossings constrain a spatial edit
    public static Vec3 constrainPoint(Vec3 original, List<DockingApproachAxis> axes, Vec3 requested){
        if(!Double.isFinite(requested.x) || !Double.isFinite(requested.y) || !Double.isFinite(requested.z)) return original;
        for(var axis : axes) if(axis.contains(original)) return axis.constrain(requested);
        return requested;
    }

    // Carry freeform edits outward while retaining each affected crossing on its own sightline
    public static SplinePathNetwork movePoint(SplinePathNetwork network, int lane, int idx, Vec3 requested, List<DockingApproachAxis> axes){
        if(!valid(network, lane, idx)) return network;
        Vec3 original = network.node(network.paths().get(lane).nodes().get(idx)).position();
        Vec3 offset = constrainPoint(original, axes, requested).subtract(original);
        Map<UUID, Vec3> moved = new HashMap<>();
        for(int next = lane; next < network.paths().size(); next++){
            UUID id = network.paths().get(next).nodes().get(idx);
            Vec3 position = network.node(id).position();
            moved.put(id, constrainPoint(position, axes, position.add(offset)));
        }
        return move(network, moved);
    }

    public static SplinePathNetwork translate(SplinePathNetwork network, int lane, Vec3 offset, List<DockingApproachAxis> axes){
        if(!valid(network, lane, 0)) return network;
        Map<UUID, Vec3> moved = new HashMap<>();
        for(UUID id : network.paths().get(lane).nodes()){
            Vec3 position = network.node(id).position();
            moved.put(id, constrainPoint(position, axes, position.add(offset)));
        }
        return move(network, moved);
    }

    // Whole-lane nudges affect only the selected lane; the primary retains its facing-axis lock
    public static SplinePathNetwork translate(SplinePathNetwork network, int lane, Vec3 offset, Vec3 facing){
        if(!valid(network, lane, 0)) return network;
        if(lane == 0) offset = facing.normalize().scale(offset.dot(facing.normalize()));
        Map<UUID, Vec3> moved = new HashMap<>();
        for(UUID id : network.paths().get(lane).nodes()) moved.put(id, network.node(id).position().add(offset));
        return move(network, moved);
    }

    public static SplinePathNetwork resize(SplinePathNetwork network, int count, Vec3 facing){
        if(!structured(network)) return network;
        int controls = network.paths().getFirst().nodes().size();
        count = Math.max(1, Math.min(count, Math.min((SplinePathNetwork.MAX_PATHS + 2) / 3,
                (SplinePathNetwork.MAX_NODES + 16) / (controls + 16))));
        List<SplinePathNetwork.Path> paths = new ArrayList<>(network.paths().subList(0, Math.min(count, network.paths().size())));
        Set<UUID> retained = new HashSet<>();
        paths.forEach(path -> retained.addAll(path.nodes()));
        List<SplinePathNetwork.Node> nodes = new ArrayList<>(network.nodes().stream().filter(node -> retained.contains(node.id())).toList());
        while(paths.size() < count){
            var source = paths.getLast();
            Map<UUID, SplinePathNetwork.Node> indexed = new HashMap<>();
            nodes.forEach(node -> indexed.put(node.id(), node));
            List<UUID> copies = new ArrayList<>();
            for(UUID id : source.nodes()){
                var node = indexed.get(id);
                UUID copy = UUID.randomUUID();
                copies.add(copy);
                Vec3 position = node.position().add(facing.normalize().scale(DEFAULT_SPACING));
                if(paths.size() == 1 && controls > 2){
                    int idx = source.nodes().indexOf(id);
                    if(idx == 0) position = position.lerp(indexed.get(source.nodes().get(1)).position().add(facing.normalize().scale(DEFAULT_SPACING)), 0.75D);
                    if(idx == controls - 1) position = position.lerp(indexed.get(source.nodes().get(controls - 2)).position().add(facing.normalize().scale(DEFAULT_SPACING)), 0.75D);
                }
                nodes.add(new SplinePathNetwork.Node(copy, position, node.role()));
            }
            paths.add(new SplinePathNetwork.Path(UUID.randomUUID(), copies));
        }
        return new SplinePathNetwork(nodes, paths);
    }

    // Insert the same spline fraction on each lane so subsequent point drags remain paired
    public static SplinePathNetwork insert(SplinePathNetwork network, int lane, int segment, Vec3 position){
        if(!valid(network, lane, segment) || segment + 1 >= network.paths().get(lane).nodes().size()
                || network.nodes().size() + network.paths().size() + 16 * (network.paths().size() - 1) > SplinePathNetwork.MAX_NODES) return network;
        var source = network.spline(network.paths().get(lane).id()).segments().get(segment);
        double fraction = source.nearestFraction(position, 0);
        if(fraction < 0.01D || fraction > 0.99D) return network;
        for(var path : network.paths()){
            Vec3 point = network.spline(path.id()).segments().get(segment).pointAtFraction(fraction);
            network = network.insert(path.id(), segment, point);
        }
        return network;
    }

    public static SplinePathNetwork removeColumn(SplinePathNetwork network, int idx, List<DockingApproachAxis> axes){
        if(network.isEmpty() || idx <= 0 || idx >= network.paths().getFirst().nodes().size() - 1) return network;
        for(var path : network.paths()){
            Vec3 position = network.node(path.nodes().get(idx)).position();
            if(axes.stream().anyMatch(axis -> axis.contains(position))) return network;
        }
        for(var path : network.paths()) network = network.remove(path.nodes().get(idx));
        return network;
    }

    public static SplinePathNetwork removeLane(SplinePathNetwork network, int lane){
        if(lane <= 0 || lane >= network.paths().size()) return network;
        return network.removeEndpoint(network.paths().get(lane).nodes().getFirst(), network.paths().getFirst().id());
    }

    // All station lanes share one traffic direction
    public static SplinePathNetwork flip(SplinePathNetwork network){
        for(var path : network.paths()) network = network.flipEndpoint(path.nodes().getFirst());
        return network;
    }

    // Connect bypasses before and after the berth row using Create's track handle calculation
    public static SplinePathNetwork connected(SplinePathNetwork lanes, Vec3 up){
        if(!structured(lanes) || lanes.paths().size() == 1) return lanes;
        var primary = lanes.paths().getFirst();
        if(primary.nodes().size() < 3) return lanes;
        List<SplinePathNetwork.Node> nodes = new ArrayList<>(lanes.nodes());
        List<SplinePathNetwork.Path> paths = new ArrayList<>(lanes.paths());
        List<UUID> primaryControls = new ArrayList<>(primary.nodes());
        List<UUID> starts = new ArrayList<>(), ends = new ArrayList<>();
        var first = lanes.spline(primary.id()).segments().getFirst();
        var last = lanes.spline(primary.id()).segments().getLast();
        for(int lane = 1; lane < lanes.paths().size(); lane++){
            double fraction = 0.1D + 0.35D * lane / lanes.paths().size();
            var path = lanes.paths().get(lane);
            UUID start = UUID.randomUUID(), end = UUID.randomUUID();
            Vec3 from = first.pointAtFraction(fraction), to = last.pointAtFraction(1 - fraction);
            starts.add(start); ends.addFirst(end);
            nodes.add(new SplinePathNetwork.Node(start, from, SplinePathNetwork.Role.JUNCTION));
            nodes.add(new SplinePathNetwork.Node(end, to, SplinePathNetwork.Role.JUNCTION));
            var spline = lanes.spline(path.id());
            addCurve(nodes, paths, start, path.nodes().getFirst(), from, lanes.node(path.nodes().getFirst()).position(),
                    first.tangentAtFraction(fraction), spline.segments().getFirst().tangentAtFraction(0), up);
            addCurve(nodes, paths, path.nodes().getLast(), end, lanes.node(path.nodes().getLast()).position(), to,
                    spline.segments().getLast().tangentAtFraction(1), last.tangentAtFraction(1 - fraction), up);
        }
        primaryControls.addAll(1, starts);
        primaryControls.addAll(primaryControls.size() - 1, ends);
        paths.set(0, new SplinePathNetwork.Path(primary.id(), primaryControls));
        Set<UUID> endpoints = new HashSet<>();
        for(int lane = 1; lane < lanes.paths().size(); lane++){
            var path = lanes.paths().get(lane);
            endpoints.add(path.nodes().getFirst()); endpoints.add(path.nodes().getLast());
        }
        return new SplinePathNetwork(nodes.stream().map(node -> endpoints.contains(node.id())
                ? new SplinePathNetwork.Node(node.id(), node.position(), SplinePathNetwork.Role.JUNCTION) : node).toList(), paths);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Old detached lanes extended to the entrance; shorten them to leave room for a forward split
    private static SplinePathNetwork trimEnds(SplinePathNetwork network){
        if(network.paths().getFirst().nodes().size() < 3) return network;
        var primary = network.paths().getFirst();
        Map<UUID, Vec3> moved = new HashMap<>();
        for(int lane = 1; lane < network.paths().size(); lane++) for(boolean start : List.of(true, false)){
            var path = network.paths().get(lane);
            int idx = start ? 0 : primary.nodes().size() - 1, next = start ? 1 : idx - 1;
            Vec3 main = network.node(primary.nodes().get(idx)).position();
            Vec3 inward = network.node(primary.nodes().get(next)).position().subtract(main);
            Vec3 position = network.node(path.nodes().get(idx)).position();
            if(position.subtract(main).dot(inward.normalize()) <= inward.length() * 0.5D)
                moved.put(path.nodes().get(idx), position.add(inward.scale(0.75D)));
        }
        return moved.isEmpty() ? network : move(network, moved);
    }

    private static void addCurve(List<SplinePathNetwork.Node> nodes, List<SplinePathNetwork.Path> paths,
            UUID fromId, UUID toId, Vec3 from, Vec3 to, Vec3 forward, Vec3 exitForward, Vec3 up){
        var curve = new BezierConnection(Couple.create(BlockPos.containing(from), BlockPos.containing(to)),
                Couple.create(from, to), Couple.create(forward.normalize(), exitForward.normalize().scale(-1)),
                Couple.create(up, up), true, false, null);
        List<UUID> controls = new ArrayList<>();
        controls.add(fromId);
        for(int idx = 1; idx < 8; idx++){
            UUID id = UUID.randomUUID();
            controls.add(id);
            nodes.add(new SplinePathNetwork.Node(id, curve.getPosition(idx / 8.0D), SplinePathNetwork.Role.JUNCTION));
        }
        controls.add(toId);
        paths.add(new SplinePathNetwork.Path(UUID.randomUUID(), controls));
    }

    private static boolean valid(SplinePathNetwork network, int lane, int idx){
        return structured(network) && lane >= 0 && lane < network.paths().size()
                && idx >= 0 && idx < network.paths().get(lane).nodes().size();
    }

    private static SplinePathNetwork move(SplinePathNetwork network, Map<UUID, Vec3> positions){
        return new SplinePathNetwork(network.nodes().stream().map(node -> new SplinePathNetwork.Node(node.id(),
                positions.getOrDefault(node.id(), node.position()), node.role())).toList(), network.paths());
    }
}
