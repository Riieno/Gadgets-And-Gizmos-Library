package com.rieno.gadgetsandgizmos.lib.control.math;

// Calculate one bounded linear feedback control value
public final class LqrControllerMath {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the LQR controller math
    private LqrControllerMath() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Calculate LQR control for a vector state
    public static double control(double target, double actual, double gain, double feedForward,
                                 double minimum, double maximum) {
        return clamp(feedForward + gain * (target - actual), minimum, maximum);
    }

    // Calculate LQR control for an array state
    public static double control(double[] target, double[] actual, double[] gain, double feedForward,
                                 double minimum, double maximum) {
        if (target.length != actual.length || actual.length != gain.length) {
            throw new IllegalArgumentException("Target, actual, and gain vectors must have equal lengths");
        }
        double output = feedForward;
        for (int idx = 0; idx < target.length; idx++) {
            output += gain[idx] * (target[idx] - actual[idx]);
        }
        return clamp(output, minimum, maximum);
    }

    // Clamp the LQR controller output
    private static double clamp(double val, double requestedMinimum, double requestedMaximum) {
        double minimum = Double.isFinite(requestedMinimum) ? requestedMinimum : -Double.MAX_VALUE;
        double maximum = Double.isFinite(requestedMaximum) ? requestedMaximum : Double.MAX_VALUE;
        if (minimum > maximum) {
            double swap = minimum;
            minimum = maximum;
            maximum = swap;
        }
        return Math.max(minimum, Math.min(maximum, val));
    }
}
