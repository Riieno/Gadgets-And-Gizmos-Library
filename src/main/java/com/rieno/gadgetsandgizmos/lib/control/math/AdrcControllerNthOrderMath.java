package com.rieno.gadgetsandgizmos.lib.control.math;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.Arrays;


// Calculate one bounded nth-order active disturbance rejection control step
public final class AdrcControllerNthOrderMath {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the nth-order ADRC controller math
    private AdrcControllerNthOrderMath(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the step
    public static Result step(State previous, int order, double target, double actual, double deltaTime,
                              double controllerBandwidth, double observerBandwidth,
                              double plantGain, double outputLimit) {
        int n = Math.max(1, order);
        int stateDim = n + 1; // Keep one extra state for the estimated disturbance

        State state = (previous == null || previous.z().length != stateDim)
                ? State.initial(n, actual)
                : previous;

        double dt = clampFinite(deltaTime, 1.0E-4D, 1.0D, 0.05D);
        double wc = clampFinite(controllerBandwidth, 1.0E-4D, 1.0E4D, 1.0D);
        double wo = clampFinite(observerBandwidth, 1.0E-4D, 1.0E4D, wc * 4.0D);
        double b0 = finiteOr(plantGain, 1.0D);
        if (Math.abs(b0) < 1.0E-9D) {
            b0 = Math.copySign(1.0E-9D, b0 == 0.0D ? 1.0D : b0);
        }

        double[] z = state.z();
        double observerError = z[0] - actual;

        // Build the observer gains from the selected order and bandwidth
        double[] l = new double[stateDim];
        double woPower = wo;
        for (int i = 0; i < stateDim; i++) {
            l[i] = nCr(stateDim, i + 1) * woPower;
            woPower *= wo;
        }

        // Move the observer forward by one Euler integration step
        double[] zNext = new double[stateDim];
        
        // Update each normal state estimate
        for (int i = 0; i < n - 1; i++) {
            zNext[i] = z[i] + dt * (z[i + 1] - l[i] * observerError);
        }
        
        // Include the previous control input in the final normal state
        zNext[n - 1] = z[n - 1] + dt * (z[n] + b0 * state.previousControl() - l[n - 1] * observerError);
        
        // Keep the disturbance estimate separate from the normal state chain
        zNext[n] = z[n] + dt * (-l[n] * observerError);

        // Start the feedback law with the target error
        double u0 = Math.pow(wc, n) * (target - zNext[0]);
        
        // Add dampening from every higher order state
        for (int i = 1; i < n; i++) {
            double ki = nCr(n, n - i) * Math.pow(wc, n - i);
            u0 -= ki * zNext[i];
        }

        // Reject the estimated disturbance and keep the result inside the output limit
        double rawControl = (u0 - zNext[n]) / b0;
        double limit = Math.abs(finiteOr(outputLimit, Double.MAX_VALUE));
        double control = Math.max(-limit, Math.min(limit, rawControl));

        State nextState = new State(zNext, control);
        return new Result(control, zNext[n], nextState);
    }

    // Calculate a binomial coefficient
    private static long nCr(int n, int r) {
        if (r < 0 || r > n) return 0;
        if (r == 0 || r == n) return 1;
        long res = 1;
        for (int i = 1; i <= r; i++) {
            res = res * (n - i + 1) / i;
        }
        return res;
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
    public record State(double[] z, double previousControl) {
        // Get the initial
        public static State initial(int order, double measurement) {
            double[] initZ = new double[order + 1];
            initZ[0] = finiteOr(measurement, 0.0D);
            return new State(initZ, 0.0D);
        }

        // Get the z
        @Override
        public double[] z() {
            return Arrays.copyOf(z, z.length);
        }
    }

    // Store the operation result
    public record Result(double control, double estimatedDisturbance, State state) {
    }
}
