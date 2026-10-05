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
