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
import java.util.List;
import java.util.UUID;

// Carry bounded, detached vehicle markers and the active route's actual world geometry
public record NavigationMapData(List<Marker> ships, Route route){
    public static final int MAX_SHIPS = 64;
    public static final int MAX_POINTS = 512;
    public static final double ROUTE_HORIZON = 512;

    public NavigationMapData{
        ships = ships == null ? List.of() : ships.stream().filter(val -> val != null && val.id() != null
                && finite(val.position()) && Double.isFinite(val.heading())).limit(MAX_SHIPS).toList();
        route = route == null ? Route.EMPTY : route;
    }

    public record Marker(UUID id, Vec3 position, double heading){}

    public record Route(Vec3 target, List<Vec3> points, boolean spline){
        public static final Route EMPTY = new Route(null, List.of(), false);
        public Route{
            target = finite(target) ? target : null;
            points = points == null ? List.of() : points.stream().filter(NavigationMapData::finite).limit(MAX_POINTS).toList();
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Retain ordered path legs; never smooth a collision planner's polyline into an unvalidated curve
    public static Route polyline(Vec3 position, Vec3 target, List<Vec3> future){
        if(!finite(position) || !finite(target)) return Route.EMPTY;
        var points = new ArrayList<Vec3>();
        points.add(position);
        if(future != null) for(Vec3 point : future){
            if(!finite(point)) continue;
            if(points.getLast().distanceToSqr(point) < 1.0E-8) continue;
            points.add(point);
            if(points.size() >= MAX_POINTS) break;
        }
        if(points.size() == 1) points.add(target);
        else if(points.size() < MAX_POINTS && points.getLast().distanceToSqr(target) > 1.0E-8) points.add(target);
        return new Route(target, points, false);
    }

    // Sample the retained curve from its monotonic cursor, including the approach to its entry
    public static Route spline(Vec3 position, Vec3 target, WaypointSpline spline, int segment, double fraction){
        if(!finite(position) || !finite(target) || spline == null || spline.isEmpty()) return polyline(position, target, List.of());
        int idx = Math.clamp(segment, 0, spline.segments().size() - 1);
        var projection = spline.projectSegment(position, idx, Math.clamp(fraction, 0, 1));
        double start = projection.distanceAlongRoute();
        double end = Math.min(spline.length(), start + ROUTE_HORIZON);
        int steps = Math.min(MAX_POINTS - 2, Math.max(1, (int)Math.ceil((end - start) / 2)));
        var points = new ArrayList<Vec3>();
        points.add(position);
        points.add(projection.position());
        for(int step = 1; step <= steps; step++) points.add(spline.pointAtDistance(start + (end - start) * step / steps).position());
        return new Route(target, points, true);
    }

    // Ground steering retains circular maneuvers rather than a waypoint spline
    public static Route curves(Vec3 position, Vec3 target, List<GroundPathPlanner.Curve> curves, int first){
        if(!finite(position) || !finite(target)) return Route.EMPTY;
        var points = new ArrayList<Vec3>();
        points.add(position);
        double remaining = ROUTE_HORIZON;
        for(int idx = Math.max(0, first); idx < curves.size() && remaining > 0 && points.size() < MAX_POINTS; idx++){
            var curve = curves.get(idx);
            double start = idx == first ? curve.nearestFraction(position) : 0;
            double length = Math.min(remaining, curve.length() * (1 - start));
            if(length < 1.0E-8) continue;
            int steps = Math.min(MAX_POINTS - points.size(), Math.max(1, (int)Math.ceil(length / 2)));
            for(int step = 1; step <= steps; step++) points.add(curve.pointAtFraction(start + length / curve.length() * step / steps));
            remaining -= length;
        }
        return points.size() > 1 ? new Route(target, points, true) : polyline(position, target, List.of());
    }

    public CompoundTag toTag(){
        var tag = new CompoundTag();
        var markers = new ListTag();
        for(Marker marker : ships){
            var row = WaypointSplineData.vector(marker.position());
            row.putUUID("Id", marker.id()); row.putDouble("Heading", marker.heading());
            markers.add(row);
        }
        tag.put("Ships", markers);
        if(route.target() != null) tag.put("Target", WaypointSplineData.vector(route.target()));
        tag.put("Route", WaypointSplineData.points(route.points()));
        tag.putBoolean("Spline", route.spline());
        return tag;
    }

    // Advance only the short interpolated display prefix, preserving later crossings and all remaining bends
    public List<Vec3> routeFrom(Vec3 position){
        var points = route.points();
        if(!finite(position) || points.size() < 2) return List.of();
        double budget = position.distanceTo(points.getFirst()) * 1.5 + 8;
        double travelled = 0, best = Double.POSITIVE_INFINITY;
        int next = 1;
        Vec3 merge = points.getFirst();
        for(int idx = 1; idx < points.size() && travelled <= budget; idx++){
            Vec3 a = points.get(idx - 1), leg = points.get(idx).subtract(a);
            double length = leg.length();
            double fraction = length < 1.0E-8 ? 0 : Math.clamp(position.subtract(a).dot(leg) / leg.lengthSqr(), 0,
                    Math.min(1, (budget - travelled) / length));
            Vec3 projected = a.add(leg.scale(fraction));
            double distance = projected.distanceToSqr(position);
            if(distance < best){ best = distance; merge = projected; next = idx; }
            travelled += length;
        }
        var res = new ArrayList<Vec3>();
        res.add(position);
        res.add(merge);
        for(int idx = next; idx < points.size() && res.size() < MAX_POINTS; idx++) res.add(points.get(idx));
        return List.copyOf(res);
    }

    public static NavigationMapData fromTag(CompoundTag tag){
        var ships = new ArrayList<Marker>();
        var rows = tag.getList("Ships", Tag.TAG_COMPOUND);
        for(int idx = 0; idx < Math.min(rows.size(), MAX_SHIPS); idx++){
            var row = rows.getCompound(idx);
            if(row.hasUUID("Id")) ships.add(new Marker(row.getUUID("Id"), WaypointSplineData.vector(row), row.getDouble("Heading")));
        }
        return new NavigationMapData(ships, new Route(tag.contains("Target") ? WaypointSplineData.vector(tag.getCompound("Target")) : null,
                WaypointSplineData.readPoints(tag.getList("Route", Tag.TAG_COMPOUND), MAX_POINTS), tag.getBoolean("Spline")));
    }

    public static boolean finite(Vec3 pos){ return pos != null && Double.isFinite(pos.x) && Double.isFinite(pos.y) && Double.isFinite(pos.z); }
}
