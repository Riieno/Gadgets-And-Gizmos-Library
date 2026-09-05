package com.rieno.gadgetsandgizmos.lib.navigation;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

// Select an immediate escape direction when a live collision probe overrides a planned route
public final class ReactiveCollisionAvoidance {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the reactive collision avoidance helper
    private ReactiveCollisionAvoidance() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Resolve one immediate collision response
    public static Response resolve(Request request) {
        Vec3 travel = normalize(request.travelDirection(), new Vec3(0.0D, 0.0D, 1.0D));
        double minimum = positive(request.minimumClearance(), 1.0D);
        double clearance = nonNegative(request.travelClearance());
        if (clearance >= minimum) return Response.clear(clearance);

        EscapeCandidate escape = request.candidates().stream()
                .filter(candidate -> candidate.clearance() > clearance + 0.25D)
                .max(Comparator.comparingDouble(candidate ->
                        escapeScore(candidate, travel, minimum)))
                .orElse(null);
        double urgency = clamp((minimum - clearance) / minimum, 0.0D, 1.0D);
        if (escape == null) {
            return new Response(true, Vec3.ZERO,
                    Math.max(0.35D, urgency), clearance, false);
        }
        return new Response(true, escape.direction(),
                Math.max(0.35D, urgency), clearance, true);
    }

    // Blend a safe escape probe into the requested travel direction without
    // replacing it with a sharp synthetic waypoint. Distant obstacles produce
    // a shallow correction; the deflection grows only as clearance closes.
    public static Vec3 steeringDirection(
            Vec3 travelDirection,
            Vec3 escapeDirection,
            double travelClearance,
            double lookahead,
            double maximumDeflectionRadians
    ) {
        Vec3 travel = normalize(travelDirection, new Vec3(0.0D, 0.0D, 1.0D));
        Vec3 escape = normalize(escapeDirection, Vec3.ZERO);
        if (escape.lengthSqr() <= 1.0E-12D) return Vec3.ZERO;
        double range = positive(lookahead, 1.0D);
        double urgency = clamp(
                (range - nonNegative(travelClearance)) / range,
                0.0D, 1.0D);
        double maximum = clamp(Math.abs(maximumDeflectionRadians),
                0.0D, Math.PI);
        double angle = Math.acos(clamp(travel.dot(escape), -1.0D, 1.0D));
        if (angle <= 1.0E-8D || maximum <= 1.0E-8D) return travel;
        double permitted = Math.min(angle,
                maximum * (0.25D + urgency * 0.75D));
        double blend = clamp(permitted / angle, 0.0D, 1.0D);
        return normalize(travel.scale(1.0D - blend)
                .add(escape.scale(blend)), travel);
    }

    // Score clearance while preferring a direction away from current travel
    private static double escapeScore(
            EscapeCandidate candidate,
            Vec3 travel,
            double minimumClearance
    ) {
        double opposition = (1.0D - candidate.direction().dot(travel)) * 0.5D;
        return candidate.clearance() + opposition * minimumClearance * 0.35D;
    }

    // Store one reactive collision request
    public record Request(Vec3 travelDirection, double travelClearance,
                          double minimumClearance,
                          List<EscapeCandidate> candidates) {
        // Initialize the collision request
        public Request {
            travelDirection = normalize(travelDirection, new Vec3(0.0D, 0.0D, 1.0D));
            travelClearance = nonNegative(travelClearance);
            minimumClearance = positive(minimumClearance, 1.0D);
            candidates = candidates == null ? List.of() : candidates.stream()
                    .filter(candidate -> candidate != null
                            && candidate.direction().lengthSqr() > 1.0E-12D)
                    .toList();
        }
    }

    // Store one host-probed escape direction
    public record EscapeCandidate(Vec3 direction, double clearance) {
        // Initialize the escape candidate
        public EscapeCandidate {
            direction = normalize(direction, Vec3.ZERO);
            clearance = nonNegative(clearance);
        }
    }

    // Store one resolved reactive response
    public record Response(boolean hazard, Vec3 escapeDirection,
                           double urgency, double travelClearance,
                           boolean escapeAvailable) {
        // Initialize the collision response
        public Response {
            escapeDirection = normalize(escapeDirection, Vec3.ZERO);
            urgency = clamp(urgency, 0.0D, 1.0D);
            travelClearance = nonNegative(travelClearance);
        }

        // Create a clear response
        public static Response clear(double clearance) {
            return new Response(false, Vec3.ZERO, 0.0D,
                    nonNegative(clearance), false);
        }
    }

    // Normalize one vector
    private static Vec3 normalize(Vec3 val, Vec3 fallback) {
        Vec3 safe = finite(val);
        if (safe.lengthSqr() > 1.0E-12D) return safe.normalize();
        safe = finite(fallback);
        return safe.lengthSqr() > 1.0E-12D ? safe.normalize() : Vec3.ZERO;
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

    // Normalize one non-negative value
    private static double nonNegative(double val) {
        return Double.isFinite(val) ? Math.max(0.0D, val) : 0.0D;
    }

    // Clamp one value
    private static double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }
}
