package com.rieno.gadgetsandgizmos.lib.navigation;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Detect lane crossings along a connector's straight outward axis
public record DockingApproachAxis(Vec3 tip, Vec3 facing, double distance){
    public static final double MAX_DISTANCE = 64;
    public static final double INTERSECTION_TOLERANCE = 0.05D;

    public DockingApproachAxis{
        facing = facing.normalize();
        distance = Double.isFinite(distance) ? Math.max(1, Math.min(MAX_DISTANCE, distance)) : 10;
    }

    public Vec3 endpoint(){ return tip.add(facing.scale(distance)); }
    public Vec3 constrain(Vec3 position){
        return tip.add(facing.scale(Math.max(1, Math.min(distance, position.subtract(tip).dot(facing)))));
    }

    public boolean contains(Vec3 position){ return position.distanceTo(constrain(position)) <= INTERSECTION_TOLERANCE; }

    // End detection at the last lane which intersects the connector's supported range
    public DockingApproachAxis followLanes(SplinePathNetwork network){
        var probe = new DockingApproachAxis(tip, facing, MAX_DISTANCE);
        var crossings = probe.intersections(network);
        for(int idx = network.paths().size() - 1; idx >= 0; idx--){
            var path = network.paths().get(idx);
            double distance = crossings.stream().filter(crossing -> crossing.pathId().equals(path.id()))
                    .mapToDouble(crossing -> crossing.position().subtract(tip).dot(facing)).max().orElse(0);
            for(UUID id : path.nodes()){
                Vec3 position = network.node(id).position();
                if(probe.contains(position)) distance = Math.max(distance, position.subtract(tip).dot(facing));
            }
            if(distance >= 1) return new DockingApproachAxis(tip, facing, distance);
        }
        return new DockingApproachAxis(tip, facing, 1);
    }

    // Retain the path identity so crossing curves do not become a junction by proximity
    public List<SplinePathNetwork.Snap> intersections(SplinePathNetwork network){
        List<SplinePathNetwork.Snap> res = new ArrayList<>();
        for(var path : network.paths()){
            var spline = network.spline(path.id());
            for(int idx = 0; idx < spline.segments().size(); idx++){
                var segment = spline.segments().get(idx);
                if(contains(segment.start()) && contains(segment.end()) && contains(segment.pointAtFraction(0.5D))){
                    UUID node = path.nodes().get(idx);
                    res.add(new SplinePathNetwork.Snap(path.id(), idx, segment.start(), node));
                    continue;
                }
                int samples = Math.max(8, Math.min(1024, (int)Math.ceil(segment.length() * 4)));
                double best = Double.MAX_VALUE;
                int sample = 0;
                for(int point = 0; point <= samples; point++){
                    Vec3 position = segment.pointAtFraction((double)point / samples);
                    double error = position.distanceToSqr(constrain(position));
                    if(error < best){ best = error; sample = point; }
                }
                double low = Math.max(0, (double)(sample - 1) / samples), high = Math.min(1, (double)(sample + 1) / samples);
                for(int step = 0; step < 32; step++){
                    double a = (low * 2 + high) / 3, b = (low + high * 2) / 3;
                    Vec3 first = segment.pointAtFraction(a), second = segment.pointAtFraction(b);
                    if(first.distanceToSqr(constrain(first)) < second.distanceToSqr(constrain(second))) high = b;
                    else low = a;
                }
                Vec3 position = segment.pointAtFraction((low + high) * 0.5D);
                if(!contains(position)) continue;
                Vec3 crossing = constrain(position);
                UUID node = null;
                if(segment.start().distanceTo(crossing) <= INTERSECTION_TOLERANCE){
                    node = path.nodes().get(idx); crossing = network.node(node).position();
                }else if(segment.end().distanceTo(crossing) <= INTERSECTION_TOLERANCE){
                    node = path.nodes().get(idx + 1); crossing = network.node(node).position();
                }
                Vec3 point = crossing;
                if(res.stream().anyMatch(prev -> prev.pathId().equals(path.id()) && prev.position().distanceTo(point) < 0.1D)) continue;
                res.add(new SplinePathNetwork.Snap(path.id(), idx, crossing, node));
            }
        }
        return List.copyOf(res);
    }

    public record BoundNetwork(SplinePathNetwork network, Map<UUID, Integer> locks){
        public BoundNetwork{ locks = Map.copyOf(locks); }
    }

    // Materialize crossing controls once so editing can constrain them to the connector axis
    public static BoundNetwork bind(SplinePathNetwork network, List<DockingApproachAxis> axes){
        Map<UUID, Integer> locks = new LinkedHashMap<>();
        for(int idx = 0; idx < axes.size(); idx++){
            var axis = axes.get(idx);
            var crossings = new ArrayList<>(axis.intersections(network));
            crossings.sort(Comparator.comparing(SplinePathNetwork.Snap::pathId).thenComparing(
                    Comparator.comparingInt(SplinePathNetwork.Snap::segment).reversed()));
            for(var crossing : crossings){
                UUID node = crossing.nodeId();
                if(node == null){
                    var updated = network.insert(crossing.pathId(), crossing.segment(), crossing.position());
                    if(updated == network) continue;
                    node = updated.paths().stream().filter(path -> path.id().equals(crossing.pathId())).findFirst().orElseThrow()
                            .nodes().get(crossing.segment() + 1);
                    network = updated;
                }
                locks.putIfAbsent(node, idx);
            }
        }
        return new BoundNetwork(network, locks);
    }
}
