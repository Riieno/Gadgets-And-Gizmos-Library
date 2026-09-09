package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.util.Mth;

/**
 * Converts a caller-owned safe speed envelope into independent acceleration,
 * deceleration and stop controls. Direction selection is deliberately absent:
 * these channels manage speed identically in forward and reverse travel.
 */
public final class ScmSpeedControl {
    private static final double EPSILON = 1.0E-5D;

    private ScmSpeedControl() {
    }

    /** Build mutually exclusive scalar speed-control demands. */
    public static Demand plan(Request request) {
        if (request == null) return Demand.IDLE;
        double actualSpeed = request.actualSpeed();
        double permittedSpeed = Math.min(
                request.permittedSpeed(), request.maximumSpeed());
        double responseBand = Math.max(0.25D,
                Math.max(1.0D, Math.max(actualSpeed, permittedSpeed))
                        * request.responseSeconds());
        if (permittedSpeed <= EPSILON) {
            double brake = actualSpeed <= EPSILON ? 0.0D
                    : Mth.clamp(actualSpeed / responseBand, 0.0D, 1.0D);
            return new Demand(0.0D, 0.0D, brake);
        }
        if (actualSpeed > permittedSpeed + EPSILON) {
            return new Demand(0.0D, Mth.clamp(
                    (actualSpeed - permittedSpeed) / responseBand,
                    0.0D, 1.0D), 0.0D);
        }
        double speedLevel = request.maximumSpeed() <= EPSILON ? 0.0D
                : request.maximumDrive() * Mth.clamp(
                permittedSpeed / request.maximumSpeed(), 0.0D, 1.0D);
        return new Demand(speedLevel, 0.0D, 0.0D);
    }

    /** Return the distance needed to observe one complete live stopping envelope. */
    public static double collisionLookahead(
            double actualSpeed,
            double minimumClearance,
            double responseSeconds,
            double brakingAcceleration,
            double minimumRange
    ) {
        double speed = Math.max(0.0D, finite(actualSpeed));
        double clearance = Math.max(0.0D, finite(minimumClearance));
        double response = Math.max(0.0D, finite(responseSeconds));
        double braking = Math.max(EPSILON, finite(brakingAcceleration));
        double range = clearance + speed * response
                + speed * speed / (2.0D * braking);
        return Math.max(Math.max(0.0D, finite(minimumRange)), range);
    }

    /** Return the maximum speed which can stop inside the supplied clearance. */
    public static double safeSpeed(
            double clearance,
            double minimumClearance,
            double responseSeconds,
            double brakingAcceleration
    ) {
        double distance = Math.max(0.0D,
                finite(clearance) - Math.max(0.0D, finite(minimumClearance)));
        double response = Math.max(0.0D, finite(responseSeconds));
        double braking = Math.max(EPSILON, finite(brakingAcceleration));
        return Math.max(0.0D, braking * (Math.sqrt(
                response * response + 2.0D * distance / braking) - response));
    }

    /** Return the maximum speed which can stop at a target capture radius. */
    public static double stoppingSpeed(
            double distance,
            double tolerance,
            double responseSeconds,
            double brakingAcceleration
    ) {
        return safeSpeed(distance, tolerance, responseSeconds, brakingAcceleration);
    }

    /**
     * Return a target approach speed which cannot asymptotically stall just
     * outside the capture radius. Collision and traffic caps remain
     * caller-owned and may still reduce this result to zero.
     */
    public static double captureApproachSpeed(
            double distance,
            double tolerance,
            double responseSeconds,
            double brakingAcceleration,
            double minimumApproachSpeed
    ) {
        double remaining = Math.max(0.0D, finite(distance));
        double capture = Math.max(0.0D, finite(tolerance));
        if (capture <= EPSILON) {
            return stoppingSpeed(
                    remaining, 0.0D, responseSeconds, brakingAcceleration);
        }
        if (remaining <= capture + EPSILON) return 0.0D;
        return Math.max(Math.max(0.0D, finite(minimumApproachSpeed)),
                stoppingSpeed(remaining, capture, responseSeconds, brakingAcceleration));
    }

    /** Inputs measured and selected by the host navigation controller. */
    public record Request(
            double actualSpeed,
            double permittedSpeed,
            double maximumSpeed,
            double maximumDrive,
            double responseSeconds
    ) {
        public Request {
            actualSpeed = Math.max(0.0D, finite(actualSpeed));
            permittedSpeed = Math.max(0.0D, finite(permittedSpeed));
            maximumSpeed = Math.max(0.0D, finite(maximumSpeed));
            maximumDrive = Mth.clamp(finite(maximumDrive), 0.0D, 1.0D);
            responseSeconds = Math.max(0.0D, finite(responseSeconds));
        }
    }

    /** Independent scalar outputs for gain/hold speed, slow down and stop. */
    public record Demand(
            double acceleration,
            double deceleration,
            double brake
    ) {
        public static final Demand IDLE = new Demand(0.0D, 0.0D, 0.0D);

        public Demand {
            acceleration = Mth.clamp(finite(acceleration), 0.0D, 1.0D);
            deceleration = Mth.clamp(finite(deceleration), 0.0D, 1.0D);
            brake = Mth.clamp(finite(brake), 0.0D, 1.0D);
        }

        /** Check that no speed-control channel can fight another. */
        public boolean exclusive() {
            int active = acceleration > EPSILON ? 1 : 0;
            active += deceleration > EPSILON ? 1 : 0;
            active += brake > EPSILON ? 1 : 0;
            return active <= 1;
        }
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0D;
    }
}
