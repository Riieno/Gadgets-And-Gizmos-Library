package com.rieno.gadgetsandgizmos.lib.navigation;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.world.phys.Vec3;

// Track waypoint capture, stalled approaches and movement away from a route
public final class WaypointProgressTracker {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Previously sampled position
    private Vec3 previousPosition = Vec3.ZERO;
    // Currently tracked waypoint
    private Vec3 waypoint = Vec3.ZERO;
    // Best measured waypoint distance
    private double bestDistance = Double.POSITIVE_INFINITY;
    // Last tick that reduced the waypoint distance
    private long lastProgressTick = Long.MIN_VALUE;
    // Best host-supplied route progress
    private double bestRouteProgress = Double.NEGATIVE_INFINITY;
    // Whether a previous sample exists
    private boolean initialized;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Observe one traversal tick
    public Observation observe(
            Vec3 position,
            Vec3 target,
            double captureRadius,
            long currentTick,
            long stalledTicks,
            boolean horizontal
    ) {
        return observe(position, target, captureRadius, currentTick,
                stalledTicks, horizontal, Double.NaN);
    }

    // Observe one traversal tick with an optional physical-curve progress value
    public Observation observe(
            Vec3 position,
            Vec3 target,
            double captureRadius,
            long currentTick,
            long stalledTicks,
            boolean horizontal,
            double routeProgress
    ) {
        Vec3 safePosition = axisPosition(position, horizontal);
        Vec3 safeTarget = axisPosition(target, horizontal);
        double radius = positive(captureRadius, 0.5D);
        long timeout = Math.max(1L, stalledTicks);
        if (!initialized || waypoint.distanceToSqr(safeTarget) > 1.0E-8D
                || currentTick < lastProgressTick) {
            reset(safePosition, safeTarget, currentTick, horizontal);
        }

        double distance = safePosition.distanceTo(safeTarget);
        double improvement = Math.max(0.05D, radius * 0.02D);
        if (distance + improvement < bestDistance) {
            bestDistance = distance;
            lastProgressTick = currentTick;
        }
        if (Double.isFinite(routeProgress)
                && routeProgress > bestRouteProgress + 0.01D) {
            bestRouteProgress = routeProgress;
            lastProgressTick = currentTick;
        }
        double sweptDistance = pointSegmentDistance(
                safeTarget, previousPosition, safePosition);
        boolean captured = distance <= radius || sweptDistance <= radius;
        long elapsed = Math.max(0L, currentTick - lastProgressTick);
        boolean regressing = !captured
                && elapsed >= Math.max(5L, timeout / 4L)
                && distance > bestDistance
                + Math.max(1.0D, Math.min(3.0D, radius * 0.25D));
        boolean stalled = !captured && elapsed >= timeout;
        previousPosition = safePosition;
        return new Observation(captured, stalled, regressing,
                distance, bestDistance, elapsed);
    }

    // Reset tracking for a new route or waypoint
    public void reset(Vec3 position, Vec3 target, long currentTick, boolean horizontal) {
        previousPosition = axisPosition(position, horizontal);
        waypoint = axisPosition(target, horizontal);
        bestDistance = previousPosition.distanceTo(waypoint);
        bestRouteProgress = Double.NEGATIVE_INFINITY;
        lastProgressTick = currentTick;
        initialized = true;
    }

    // Clear every retained progress sample
    public void clear() {
        previousPosition = Vec3.ZERO;
        waypoint = Vec3.ZERO;
        bestDistance = Double.POSITIVE_INFINITY;
        bestRouteProgress = Double.NEGATIVE_INFINITY;
        lastProgressTick = Long.MIN_VALUE;
        initialized = false;
    }

    // Get the distance between one point and one segment
    private static double pointSegmentDistance(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 segment = end.subtract(start);
        double lengthSqr = segment.lengthSqr();
        if (lengthSqr <= 1.0E-12D) return point.distanceTo(start);
        double progress = clamp(point.subtract(start).dot(segment) / lengthSqr, 0.0D, 1.0D);
        return point.distanceTo(start.add(segment.scale(progress)));
    }

    // Project one position onto the tracked movement plane
    private static Vec3 axisPosition(Vec3 value, boolean horizontal) {
        Vec3 safe = finite(value);
        return horizontal ? new Vec3(safe.x, 0.0D, safe.z) : safe;
    }

    // Normalize one vector
    private static Vec3 finite(Vec3 val) {
        return val == null || !Double.isFinite(val.x)
                || !Double.isFinite(val.y) || !Double.isFinite(val.z)
                ? Vec3.ZERO : val;
    }

    // Normalize one positive value
    private static double positive(double val, double fallback) {
        return Double.isFinite(val) && val > 0.0D ? val : fallback;
    }

    // Clamp one value
    private static double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    // Store one progress observation
    public record Observation(boolean captured, boolean stalled,
                              boolean regressing, double distance,
                              double bestDistance, long ticksWithoutProgress) {
        // Check whether the current route must be replaced
        public boolean requiresReplan() {
            return stalled || regressing;
        }
    }
}
