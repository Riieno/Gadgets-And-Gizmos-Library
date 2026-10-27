package com.rieno.gadgetsandgizmos.lib.navigation;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Collection;
import java.util.Optional;

// Separate the reverse docking translation from forward travel along the exit lane
public record DockingDeparturePlan(Vec3 tip, Vec3 facing, Vec3 up, Vec3 clearancePoint, List<Vec3> exitControls){
    public DockingDeparturePlan{ exitControls = List.copyOf(exitControls); }

    // Leave through the crossing used on arrival rather than selecting another lane through the dock
    public static DockingDeparturePlan create(DockingApproachPath approach, Vec3 tip, Vec3 facing, Vec3 up, Vec3 arrival){
        Vec3 axis = facing.normalize();
        Vec3 clearance = tip.add(axis.scale(Math.max(1.0D, Math.min(64.0D, arrival.subtract(tip).dot(axis)))));
        var world = approach.worldNetwork(tip, facing, up);
        var crossings = new DockingApproachAxis(tip, facing, approach.distance()).intersections(world);
        var crossing = crossings.stream().min(java.util.Comparator.comparingDouble(val -> val.position().distanceToSqr(clearance)));
        if(crossing.isEmpty()) return new DockingDeparturePlan(tip, axis, up, clearance, List.of());
        var selected = crossing.get();
        var route = SplinePathNetwork.selectPrimary(world.routesAt(selected, true, null), null, null);
        return new DockingDeparturePlan(tip, axis, up, selected.position(),
                route.map(SplinePathNetwork.Route::controls).orElse(List.of()));
    }

    // Choose the reverse leg and its forward exit together using current and provisioned traffic
    public static Optional<DockingDeparturePlan> select(DockingApproachPath approach, Vec3 tip, Vec3 facing, Vec3 up,
            Vec3 arrival, List<AABB> hull, Collection<DockTrafficGeometry.Obstacle> obstacles, DockTrafficGeometry.Traffic traffic){
        if(!approach.configured()) return Optional.of(create(approach, tip, facing, up, arrival));
        var world = approach.worldNetwork(tip, facing, up);
        List<SplinePathNetwork.Route> available = new ArrayList<>();
        java.util.Map<SplinePathNetwork.Route, DockingDeparturePlan> plans = new java.util.HashMap<>();
        for(var crossing : new DockingApproachAxis(tip, facing, approach.distance()).intersections(world)){
            for(var route : world.routesAt(crossing, true, controls -> DockTrafficGeometry.occupancyCost(controls, hull, obstacles)
                    + DockTrafficGeometry.trafficCost(controls, hull, traffic) + DockTrafficGeometry.detourCost(world, controls))){
                List<Vec3> controls = new ArrayList<>();
                controls.add(tip);
                controls.addAll(route.controls());
                if(DockTrafficGeometry.blocker(controls, hull, obstacles) != null) continue;
                var candidate = new SplinePathNetwork.Route(route.endpointId(), route.endpoint(), controls,
                        tip.distanceTo(crossing.position()) + route.length(),
                        Math.max(route.priority(), DockingApproachLanes.laneIndex(world, crossing.pathId())));
                available.add(candidate);
                plans.put(candidate, new DockingDeparturePlan(tip, facing.normalize(), up, crossing.position(), route.controls()));
            }
        }
        return DockTrafficGeometry.selectRoute(available, null, hull, traffic).map(plans::get);
    }

    // Protect the complete hull sweep between the connector and its lane crossing
    public List<AABB> clearanceBounds(List<AABB> relativeHull){
        List<AABB> res = new ArrayList<>();
        for(AABB hull : relativeHull) res.add(hull.move(tip).minmax(hull.move(clearancePoint)).inflate(1.0D));
        return List.copyOf(res);
    }

    // Release a berth only after every part of the departing hull has cleared its docking sweep
    public static boolean clear(List<AABB> ship, List<AABB> protectedBounds){
        return !ship.isEmpty() && ship.stream().noneMatch(hull -> protectedBounds.stream().anyMatch(hull::intersects));
    }

    public CompoundTag toTag(){
        CompoundTag tag = new CompoundTag();
        tag.put("Frame", WaypointSplineData.points(List.of(tip, facing, up, clearancePoint)));
        tag.put("Exit", WaypointSplineData.points(exitControls));
        return tag;
    }

    public static DockingDeparturePlan fromTag(CompoundTag tag){
        List<Vec3> frame = WaypointSplineData.readPoints(tag.getList("Frame", Tag.TAG_COMPOUND), 4);
        if(frame.size() != 4) throw new IllegalArgumentException("Invalid departure frame");
        return new DockingDeparturePlan(frame.get(0), frame.get(1), frame.get(2), frame.get(3),
                WaypointSplineData.readPoints(tag.getList("Exit", Tag.TAG_COMPOUND), 131072));
    }
}
