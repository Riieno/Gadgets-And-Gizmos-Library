package com.rieno.gadgetsandgizmos.lib.scm;

// Design and apply second-order state feedback for vehicle position and attitude axes
public final class ScmStateFeedback {
    private ScmStateFeedback() {
    }

    // Solve the two-state continuous LQR for position, rate and control effort weights
    public static Gains lqr(double positionWeight, double rateWeight, double effortWeight) {
        double position = positive(positionWeight);
        double rate = Math.max(0.0D, finite(rateWeight));
        double effort = positive(effortWeight);
        double positionGain = Math.sqrt(position / effort);
        double rateGain = Math.sqrt(rate / effort + 2.0D * positionGain);
        return new Gains(positionGain, rateGain);
    }

    // Choose LQR weights for two equal stable poles at the requested response time
    public static Gains lqrForResponse(double responseSeconds) {
        double frequency = 1.0D / positive(responseSeconds);
        return lqr(frequency * frequency * frequency * frequency,
                2.0D * frequency * frequency, 1.0D);
    }

    // Place the two closed-loop poles of a unit-acceleration axis
    public static Gains ackermann(double firstPole, double secondPole) {
        if (!Double.isFinite(firstPole) || !Double.isFinite(secondPole)
                || firstPole >= 0.0D || secondPole >= 0.0D) {
            throw new IllegalArgumentException("Feedback poles must be finite and negative");
        }
        return new Gains(firstPole * secondPole, -firstPole - secondPole);
    }

    // Apply feedback to a measured position error and angular or linear rate
    public static double acceleration(double error, double rate, Gains gains) {
        if (gains == null) return 0.0D;
        return finite(gains.position() * finite(error) - gains.rate() * finite(rate));
    }

    // Track a desired velocity with a bounded response time
    public static double velocityAcceleration(double desiredVelocity, double velocity,
                                              double responseSeconds){
        return (finite(desiredVelocity) - finite(velocity)) / positive(responseSeconds);
    }

    // Recover with stable poles while propulsion holds acceleration through each control interval
    public static double recoveryAcceleration(double error, double rate, double strength, double tickSeconds){
        double dt = positive(tickSeconds);
        double fraction = ScmControlInputs.strength(strength);
        double gain = fraction / dt;
        return finite(gain * gain * finite(error) - gain * (2.0D - 0.5D * fraction) * finite(rate));
    }

    // Keep altitude feedback stable while a lift source fills or cools toward its target
    public static double delayedRecoveryAcceleration(double error, double rate, double strength,
                                                     double tickSeconds, double responseTimeSeconds){
        double response = Double.isFinite(responseTimeSeconds) ? Math.max(0.0D, responseTimeSeconds) : 0.0D;
        return recoveryAcceleration(error, rate, strength, Math.max(positive(tickSeconds), response));
    }

    // Limit the approach speed by braking authority before requesting bounded acceleration
    public static double recoveryAcceleration(double error, double rate, double strength, double tickSeconds,
                                               double positiveAuthority, double negativeAuthority){
        double dt = positive(tickSeconds);
        double fraction = ScmControlInputs.strength(strength);
        if(fraction == 0.0D) return 0.0D;
        double displacement = finite(error);
        double positive = Math.max(0.0D, finite(positiveAuthority));
        double negative = Math.max(0.0D, finite(negativeAuthority));
        double damping = fraction * (2.0D - 0.5D * fraction) / dt;
        double targetRate = displacement * fraction / (dt * (2.0D - 0.5D * fraction));
        double braking = displacement >= 0.0D ? negative : positive;
        if(braking > 0.0D && displacement != 0.0D){
            double horizon = 1.0D / damping;
            double safeRate = 2.0D * Math.abs(displacement)
                    / (Math.sqrt(horizon * horizon + 2.0D * Math.abs(displacement) / braking) + horizon);
            targetRate = Math.copySign(Math.min(Math.abs(targetRate), safeRate), displacement);
        }
        return Math.clamp(finite((targetRate - finite(rate)) * damping), -negative, positive);
    }

    // At full strength request a rate change within one control interval
    public static double recoveryVelocityAcceleration(double desiredRate, double rate,
                                                       double strength, double tickSeconds){
        double dt = positive(tickSeconds);
        double fraction = ScmControlInputs.strength(strength);
        if(fraction == 0.0D) return 0.0D;
        return finite((finite(desiredRate) - finite(rate)) * fraction / dt);
    }

    private static double positive(double val) {
        if (!Double.isFinite(val) || val <= 0.0D) {
            throw new IllegalArgumentException("LQR weights must be finite and positive");
        }
        return val;
    }

    private static double finite(double val) {
        return Double.isFinite(val) ? val : 0.0D;
    }

    public record Gains(double position, double rate) {
        public Gains {
            if (!Double.isFinite(position) || !Double.isFinite(rate)
                    || position <= 0.0D || rate <= 0.0D) {
                throw new IllegalArgumentException("Feedback gains must be finite and positive");
            }
        }
    }
}
