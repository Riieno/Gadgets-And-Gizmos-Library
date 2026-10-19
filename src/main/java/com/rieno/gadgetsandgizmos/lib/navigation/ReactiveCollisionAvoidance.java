package com.rieno.gadgetsandgizmos.lib.navigation;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

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

    // Motion away from a surface must not inhibit propulsion that leaves it
    public static boolean probeMotionDirection(Vec3 velocity, Vec3 travelDirection, double minimumSpeed){
        return velocity != null && travelDirection != null
                && velocity.lengthSqr() > Math.max(0.0D, minimumSpeed) * Math.max(0.0D, minimumSpeed)
                && velocity.dot(travelDirection) > 0.0D;
    }

    // Keep translation available throughout an escape before restoring the preferred heading
    public static boolean retainForwardPreference(boolean requested, boolean escapeActive){
        return requested && !escapeActive;
    }

    // Detect nearby bodies even when the controlled craft has no requested travel
    public static ProximityHazard proximityHazard(List<AABB> bounds, Vec3 velocity,
            List<MovingObstacle> obstacles, double clearance, double responseSeconds){
        double margin = positive(clearance, 1.0D);
        double horizon = Math.max(0.0D, responseSeconds);
        ProximityHazard res = ProximityHazard.NONE;
        double risk = Double.POSITIVE_INFINITY;
        for(AABB hull : bounds) for(MovingObstacle obstacle : obstacles){
            AABB other = obstacle.bounds();
            double dx = axisGap(hull.minX, hull.maxX, other.minX, other.maxX);
            double dy = axisGap(hull.minY, hull.maxY, other.minY, other.maxY);
            double dz = axisGap(hull.minZ, hull.maxZ, other.minZ, other.maxZ);
            double squared = dx * dx + dy * dy + dz * dz;
            double vx = obstacle.velocity().x - velocity.x, vy = obstacle.velocity().y - velocity.y,
                    vz = obstacle.velocity().z - velocity.z;
            double maximum = margin + Math.sqrt(vx * vx + vy * vy + vz * vz) * horizon;
            if(squared >= maximum * maximum) continue;
            double gap = Math.sqrt(squared);
            if(squared <= 1.0E-12D){
                dx = (other.minX + other.maxX) * 0.5D - (hull.minX + hull.maxX) * 0.5D;
                dy = (other.minY + other.maxY) * 0.5D - (hull.minY + hull.maxY) * 0.5D;
                dz = (other.minZ + other.maxZ) * 0.5D - (hull.minZ + hull.maxZ) * 0.5D;
            }
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if(length <= 1.0E-6D){ dx = 1.0D; dy = 0.0D; dz = 0.0D; }
            else{ dx /= length; dy /= length; dz /= length; }
            double closing = Math.max(0.0D, -(vx * dx + vy * dy + vz * dz));
            double required = margin + closing * horizon;
            if(gap >= required) continue;
            // Crossing traffic must enter the inflated hull during the response interval
            if(gap >= margin){
                AABB corridor = hull.inflate(margin + other.getXsize() * 0.5D,
                        margin + other.getYsize() * 0.5D, margin + other.getZsize() * 0.5D);
                Vec3 center = other.getCenter();
                if(corridor.clip(center, center.add(vx * horizon, vy * horizon, vz * horizon)).isEmpty()) continue;
            }
            double score = gap / required;
            if(score < risk){ risk = score; res = new ProximityHazard(new Vec3(dx, dy, dz), gap, required); }
        }
        return res;
    }

    // Prefer motion which increases separation from an approaching body
    public static Response resolveProximity(ProximityHazard hazard, List<EscapeCandidate> candidates){
        if(hazard == null || !hazard.available()) return Response.clear(0.0D);
        EscapeCandidate escape = candidates.stream().filter(candidate -> candidate.clearance() > 0.25D
                && candidate.direction().dot(hazard.direction()) < -0.01D)
                .max(Comparator.comparingDouble(candidate -> -candidate.direction().dot(hazard.direction())
                        * Math.min(candidate.clearance(), hazard.requiredClearance() + 1.0D)))
                .orElse(null);
        if(escape == null) escape = candidates.stream().filter(candidate -> candidate.clearance() > 0.25D)
                .max(Comparator.comparingDouble(EscapeCandidate::clearance)).orElse(null);
        return new Response(true, escape == null ? Vec3.ZERO : escape.direction(), 1.0D,
                hazard.clearance(), escape != null);
    }

    // Stop probing when the direct departure already has the greatest possible escape score
    public static ProximityEscape resolveProximity(ProximityHazard hazard, List<Vec3> directions,
            Function<Vec3, EscapeCandidate> probe){
        if(hazard == null || !hazard.available()) return new ProximityEscape(Response.clear(0.0D), 0.0D);
        List<EscapeCandidate> candidates = new ArrayList<>(directions.size());
        for(Vec3 direction : directions){
            EscapeCandidate candidate = probe.apply(direction);
            candidates.add(candidate);
            if(candidate.direction().dot(hazard.direction()) <= -1.0D
                    && candidate.clearance() >= hazard.requiredClearance() + 1.0D)
                return new ProximityEscape(new Response(true, candidate.direction(), 1.0D,
                        hazard.clearance(), true), candidate.clearance());
        }
        Response res = resolveProximity(hazard, candidates);
        double clearance = candidates.stream().filter(candidate -> candidate.direction().dot(res.escapeDirection()) > 0.999D)
                .mapToDouble(EscapeCandidate::clearance).min().orElse(0.0D);
        return new ProximityEscape(res, clearance);
    }

    public record ProximityEscape(Response response, double clearance){}

    public record MovingObstacle(AABB bounds, Vec3 velocity){}

    public record ProximityHazard(Vec3 direction, double clearance, double requiredClearance){
        public static final ProximityHazard NONE = new ProximityHazard(Vec3.ZERO, 0.0D, 0.0D);
        public boolean available(){ return direction.lengthSqr() > 1.0E-12D; }
    }

    private static double axisGap(double min, double max, double otherMin, double otherMax){
        return otherMin > max ? otherMin - max : otherMax < min ? otherMax - min : 0.0D;
    }

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

    // Score clearance while preferring the least disruptive safe direction.
    private static double escapeScore(
            EscapeCandidate candidate,
            Vec3 travel,
            double minimumClearance
    ) {
        double alignment = (1.0D + candidate.direction().dot(travel)) * 0.5D;
        return candidate.clearance() + alignment * minimumClearance * 0.35D;
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
