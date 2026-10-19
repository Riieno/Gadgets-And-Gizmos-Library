package com.rieno.gadgetsandgizmos.lib.navigation;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Build vehicle-type-aware centreline capture guidance for an authored spline. */
public final class SplineMagnetism {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the spline magnet helper
    private SplineMagnetism() {
    }

    /** Pull a vehicle towards the route while retaining the ordered route tangent. */
    public static Guidance guide(
            Vec3 position,
            WaypointSpline.Projection projection,
            double lookahead,
            double attraction,
            AxisPolicy axes
    ) {
        return guide(position, Vec3.ZERO, projection, lookahead,
                attraction, 0.0D, axes);
    }

    /** Pull a moving vehicle towards the route and damp velocity across its centreline. */
    public static Guidance guide(
            Vec3 position,
            Vec3 velocity,
            WaypointSpline.Projection projection,
            double lookahead,
            double attraction,
            double lateralDamping,
            AxisPolicy axes
    ) {
        if (position == null || projection == null || !projection.found()) {
            return Guidance.none();
        }
        AxisPolicy policy = axes == null ? AxisPolicy.ALL : axes;
        Vec3 tangent = policy.filter(projection.tangent());
        if (tangent.lengthSqr() <= 1.0E-12D) return Guidance.none();
        tangent = tangent.normalize();
        Vec3 offset = policy.filter(projection.position().subtract(position));
        Vec3 crossTrack = offset.subtract(tangent.scale(offset.dot(tangent)));
        Vec3 motion = policy.filter(velocity);
        Vec3 lateralVelocity = motion.subtract(tangent.scale(motion.dot(tangent)));
        double lead = Math.max(0.5D, finite(lookahead));
        double strength = Mth.clamp(finite(attraction), 0.0D, 8.0D);
        double damping = Mth.clamp(finite(lateralDamping), 0.0D, 8.0D);
        Vec3 correction = crossTrack.scale(strength)
                .subtract(lateralVelocity.scale(damping));
        double maximumCorrection = lead * 3.0D;
        if (correction.lengthSqr() > maximumCorrection * maximumCorrection) {
            correction = correction.normalize().scale(maximumCorrection);
        }
        Vec3 travel = tangent.scale(lead).add(correction);
        travel = travel.lengthSqr() <= 1.0E-12D ? tangent : travel.normalize();
        return new Guidance(true, travel, tangent, crossTrack, crossTrack.length());
    }

    // Normalize one finite scalar
    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0D;
    }

    /** Select the axes on which the centreline may attract a vehicle. */
    public enum AxisPolicy {
        ALL,
        HORIZONTAL;

        // Filter one vector to this policy
        private Vec3 filter(Vec3 value) {
            Vec3 resolved = value == null ? Vec3.ZERO : value;
            return this == HORIZONTAL
                    ? new Vec3(resolved.x, 0.0D, resolved.z) : resolved;
        }
    }

    /** One centreline capture direction and its independent route orientation. */
    public record Guidance(boolean active, Vec3 travelDirection, Vec3 facingDirection,
                           Vec3 crossTrackCorrection, double crossTrackDistance) {
        // Initialize the spline guidance
        public Guidance {
            travelDirection = travelDirection == null ? Vec3.ZERO : travelDirection;
            facingDirection = facingDirection == null ? Vec3.ZERO : facingDirection;
            crossTrackCorrection = crossTrackCorrection == null
                    ? Vec3.ZERO : crossTrackCorrection;
            crossTrackDistance = Math.max(0.0D, finite(crossTrackDistance));
        }

        // Create an inactive guidance result
        public static Guidance none() {
            return new Guidance(false, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 0.0D);
        }
    }
}
