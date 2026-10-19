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

    // Convert an authored speed percentage to positive-only available power
    public static double percentage(double val){
        return Mth.clamp(finite(val) / 100.0D, 0.0D, 1.0D);
    }

    // A clear finite scan is not an obstacle at the scan's far boundary
    public static double scannedSafeSpeed(double clearance, double scanRange, double requestedSpeed,
                                         double minimumClearance, double responseSeconds, double brakingAcceleration){
        double requested = Math.max(0.0D, finite(requestedSpeed));
        if(scanRange > 0.0D && clearance >= scanRange - 1.0E-4D) return requested;
        return Math.min(requested, safeSpeed(clearance, minimumClearance, responseSeconds, brakingAcceleration));
    }

    // Preserve automatic propulsion instead of turning its sentinel into a stop command
    public static double propulsionRequest(double val){
        return Double.isFinite(val) && val >= 0.0D ? Mth.clamp(val, 0.0D, 1.0D) : -1.0D;
    }

    // A positive analogue request must not round to an inactive discrete signal
    public static int quantizedSignal(double level, int maximumSignal){
        int maximum = Math.max(0, maximumSignal);
        double requested = Mth.clamp(finite(level), 0.0D, 1.0D);
        return maximum == 0 || requested <= EPSILON ? 0
                : Math.max(1, Math.min(maximum, (int) Math.round(requested * maximum)));
    }

    // Keep tiny boundary jitter from repeatedly rebuilding a discrete kinetic drive network
    public static int quantizedSignal(double level, int maximumSignal, int prevSignal, double hysteresis){
        int next = quantizedSignal(level, maximumSignal);
        if(next == 0 || prevSignal <= 0 || prevSignal > maximumSignal) return next;
        double scaled = Mth.clamp(finite(level), 0.0D, 1.0D) * maximumSignal;
        double band = 0.5D + Mth.clamp(finite(hysteresis), 0.0D, 0.49D);
        return Math.abs(scaled - prevSignal) <= band ? prevSignal : next;
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
        double speedError = Math.max(0.0D, permittedSpeed - actualSpeed);
        double speedLevel = request.maximumSpeed() <= EPSILON ? 0.0D
                : request.maximumDrive() * Math.min(
                Mth.clamp(permittedSpeed / request.maximumSpeed(), 0.0D, 1.0D),
                Mth.clamp(speedError / responseBand, 0.0D, 1.0D));
        return new Demand(speedLevel, 0.0D, 0.0D);
    }

    // Hold an analogue speed setpoint when a separate controller owns actual speed
    public static Demand planSpeedGroup(Request request){
        if(request == null) return Demand.IDLE;
        double permittedSpeed = Math.min(request.permittedSpeed(), request.maximumSpeed());
        if(permittedSpeed <= EPSILON) return plan(request);
        if(request.maximumDrive() <= EPSILON) return Demand.IDLE;
        double level = request.maximumDrive()
                * Mth.clamp(permittedSpeed / request.maximumSpeed(), 0.0D, 1.0D);
        if(request.actualSpeed() > permittedSpeed + EPSILON){
            double responseBand = Math.max(0.25D,
                    Math.max(1.0D, Math.max(request.actualSpeed(), permittedSpeed))
                            * request.responseSeconds());
            double brake = Mth.clamp(
                    (request.actualSpeed() - permittedSpeed) / responseBand,
                    0.0D, 1.0D);
            return new Demand(level, 0.0D, brake);
        }
        return new Demand(level, 0.0D, 0.0D);
    }

    // Keep an engine setpoint active independently of cooperative braking
    public static double accelerationSetpoint(double acceleration, double sustainingPower){
        return Math.max(Mth.clamp(finite(acceleration), 0.0D, 1.0D),
                Mth.clamp(finite(sustainingPower), 0.0D, 1.0D));
    }

    // Keep acceleration positive-only and let an explicit slowdown or stop take priority
    public static double accelerationSignal(double acceleration, double deceleration, double brake){
        return accelerationSignal(acceleration, deceleration, brake, 0.0D);
    }

    // Travel braking must not remove power needed by an independent lift controller
    public static double accelerationSignal(
            double acceleration, double deceleration, double brake, double sustainingPower
    ){
        double drive = Math.max(0.0D, finite(deceleration)) > EPSILON
                || Math.max(0.0D, finite(brake)) > EPSILON
                ? 0.0D : Mth.clamp(finite(acceleration), 0.0D, 1.0D);
        return Math.max(drive, Mth.clamp(finite(sustainingPower), 0.0D, 1.0D));
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
