package com.rieno.gadgetsandgizmos.lib.navigation;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

// Keep steering previews and merges on the current ordered spline suffix
public final class SplineRouteGeometry{
    private SplineRouteGeometry(){}

    // Give each dock an editable approach and departure handle plus a leg midpoint
    public static List<Vec3> editableLeg(Vec3 origin, Vec3 destination, double handleDistance){
        if(origin == null || destination == null || origin.distanceToSqr(destination) <= 1.0E-8D) return List.of();
        Vec3 leg = destination.subtract(origin);
        double distance = Math.min(leg.length() * 0.2D, Math.max(0.05D, finite(handleDistance)));
        Vec3 offset = leg.normalize().scale(distance);
        return List.of(origin, origin.add(offset), origin.lerp(destination, 0.5D), destination.subtract(offset), destination);
    }

    // Sample only the transit interior so intentional dock contact is never reported as a route obstruction
    public static List<Vec3> transitInterior(WaypointSpline spline, double maximumSpacing,
                                              int maximumSamples, double endpointClearance){
        if(spline == null || spline.isEmpty() || maximumSamples < 2) return List.of();
        double inset = Math.max(0.0D, finite(endpointClearance));
        double start = Math.min(inset, spline.length());
        double end = Math.max(start, spline.length() - inset);
        double distance = end - start;
        if(distance <= 1.0E-4D) return List.of();
        double spacing = Math.max(0.05D, finite(maximumSpacing));
        int chords = Math.max(1, Math.min(maximumSamples - 1,
                (int) Math.ceil(distance / spacing)));
        List<Vec3> points = new ArrayList<>(chords + 1);
        for(int idx = 0; idx <= chords; idx++){
            points.add(spline.pointAtDistance(start + distance * idx / chords).position());
        }
        return List.copyOf(points);
    }

    // Select one ordered merge point beyond both the vehicle and blocker while preserving a terminal handoff
    public static WaypointSpline.TrackingTarget detourMergeTarget(WaypointSpline spline,
            WaypointSpline.Projection vehicle, WaypointSpline.Projection blocker,
            double blockerClearance, double minimumAdvance, double terminalClearance){
        if(spline == null || spline.isEmpty() || vehicle == null || !vehicle.found()
                || blocker == null || !blocker.found()) return WaypointSpline.TrackingTarget.notFound();
        double distance = Math.max(vehicle.distanceAlongRoute() + Math.max(0.0D, finite(minimumAdvance)),
                blocker.distanceAlongRoute() + Math.max(0.0D, finite(blockerClearance)));
        double terminal = spline.length() - Math.max(0.0D, finite(terminalClearance));
        if(distance > terminal || distance <= vehicle.distanceAlongRoute() + 1.0E-4D){
            return WaypointSpline.TrackingTarget.notFound();
        }
        WaypointSpline.RoutePoint point = spline.pointAtDistance(distance);
        return new WaypointSpline.TrackingTarget(true, vehicle, point.segmentIndex(), point.fraction(),
                point.position(), point.tangent(), distance);
    }

    // Replace only the active blocked interval while retaining completed points and the untouched suffix
    public static RouteSplice spliceDetour(List<Vec3> retained, int waypointIndex,
                                            List<Vec3> detour, int rejoinWaypointIndex){
        List<Vec3> route = retained == null ? List.of() : retained;
        int insertion = Math.max(0, Math.min(waypointIndex, route.size()));
        int suffix = Math.max(insertion, Math.min(rejoinWaypointIndex, route.size()));
        List<Vec3> merged = new ArrayList<>();
        for(int idx = 0; idx < insertion; idx++) append(merged, route.get(idx));
        if(detour != null){
            for(Vec3 point : detour) append(merged, point);
        }
        int resume = merged.size();
        for(int idx = suffix; idx < route.size(); idx++) append(merged, route.get(idx));
        return new RouteSplice(merged,
                Math.min(insertion, Math.max(0, merged.size() - 1)), resume);
    }

    // Project a steering axle locally without committing the hull's progress
    public static WaypointSpline.Projection steeringProjection(WaypointSpline spline,
            WaypointSpline.Projection center, Vec3 reference, double previewDistance){
        if(spline == null || center == null || !center.found() || reference == null) return center;
        WaypointSpline.Projection best = spline.projectSegment(reference, center.segmentIndex(), 0.0D);
        double horizon = center.distanceAlongRoute() + Math.max(0.75D, finite(previewDistance));
        for(int idx = center.segmentIndex() + 1; idx < spline.segments().size(); idx++){
            WaypointSpline.Projection start = spline.projectSegment(reference, idx, 0.0D);
            if(start.distanceAlongRoute() > horizon) break;
            if(start.distance() < best.distance()) best = start;
        }
        return best;
    }

    // Pick a forward curve point and its tangent instead of merging sideways
    public static WaypointSpline.TrackingTarget mergeTarget(WaypointSpline spline,
            WaypointSpline.Projection projection, Vec3 position, double turningRadius,
            double minimumAdvance, SplineConstraintFrame.AxisPolicy axes){
        if(spline == null || projection == null || !projection.found() || position == null){
            return WaypointSpline.TrackingTarget.notFound();
        }
        SplineConstraintFrame.AxisPolicy policy = axes == null
                ? SplineConstraintFrame.AxisPolicy.ALL : axes;
        double distance = policy.filter(position.subtract(projection.position())).length();
        double advance = Math.max(Math.max(0.75D, finite(minimumAdvance)),
                Math.max(Math.max(0.0D, finite(turningRadius)) * 2.5D, distance * 1.75D));
        return spline.trackingTarget(projection, advance);
    }

    // Normalize one caller-supplied distance
    private static double finite(double val){
        return Double.isFinite(val) ? val : 0.0D;
    }

    // Add one finite non-repeating route point
    private static void append(List<Vec3> points, Vec3 point){
        if(point != null && Double.isFinite(point.x) && Double.isFinite(point.y)
                && Double.isFinite(point.z) && (points.isEmpty()
                || points.getLast().distanceToSqr(point) > 1.0E-8D)) points.add(point);
    }

    // Describe an active-prefix splice without changing a host's route state
    public record RouteSplice(List<Vec3> waypoints, int waypointIndex, int resumeWaypointIndex){
        public RouteSplice{
            waypoints = waypoints == null ? List.of() : List.copyOf(waypoints);
            waypointIndex = Math.max(0, Math.min(waypointIndex,
                    Math.max(0, waypoints.size() - 1)));
            resumeWaypointIndex = Math.max(0, Math.min(resumeWaypointIndex, waypoints.size()));
        }
    }
}
