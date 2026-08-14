package com.rieno.gadgetsandgizmos.lib.control.math;

// Calculate one bounded active disturbance rejection control step
public final class AdrcControllerMath {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ADRC controller math
    private AdrcControllerMath() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the step
    public static Result step(State previous, double target, double actual, double deltaTime,
                              double controllerBandwidth, double observerBandwidth,
                              double plantGain, double outputLimit) {
        State state = previous == null ? State.initial(actual) : previous;
        double dt = clampFinite(deltaTime, 1.0E-4D, 1.0D, 0.05D);
        double wc = clampFinite(controllerBandwidth, 1.0E-4D, 1.0E4D, 1.0D);
        double wo = clampFinite(observerBandwidth, 1.0E-4D, 1.0E4D, wc * 4.0D);
        double b0 = finiteOr(plantGain, 1.0D);
        if (Math.abs(b0) < 1.0E-9D) {
            b0 = Math.copySign(1.0E-9D, b0 == 0.0D ? 1.0D : b0);
        }

        double observerError = state.estimatedState() - actual;
        double estimatedState = state.estimatedState()
                + dt * (state.estimatedDisturbance() - 2.0D * wo * observerError
                + b0 * state.previousControl());
        double estimatedDisturbance = state.estimatedDisturbance()
                - dt * wo * wo * observerError;
        double rawControl = (wc * wc * (target - estimatedState) - estimatedDisturbance) / b0;
        double limit = Math.abs(finiteOr(outputLimit, Double.MAX_VALUE));
        double control = Math.max(-limit, Math.min(limit, rawControl));
        State next = new State(estimatedState, estimatedDisturbance, control);
        return new Result(control, estimatedDisturbance, next);
    }

    // Clamp the value to a finite range
    private static double clampFinite(double val, double minimum, double maximum, double fallback) {
        return Math.max(minimum, Math.min(maximum, finiteOr(val, fallback)));
    }

    // Use the fallback when the value is not finite
    private static double finiteOr(double val, double fallback) {
        return Double.isFinite(val) ? val : fallback;
    }

    // Store the current state
    public record State(double estimatedState, double estimatedDisturbance, double previousControl) {
        // Get the initial
        public static State initial(double measurement) {
            return new State(finiteOr(measurement, 0.0D), 0.0D, 0.0D);
        }
    }

    // Store the operation result
    public record Result(double control, double estimatedDisturbance, State state) {
    }
}
