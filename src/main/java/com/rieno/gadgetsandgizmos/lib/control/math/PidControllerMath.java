package com.rieno.gadgetsandgizmos.lib.control.math;

// Advance and bound the integral term used by a PID controller
public final class PidControllerMath {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the PID controller math
    private PidControllerMath() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the next integral
    public static double nextIntegral(double previous, double error, boolean preventWindup,
                                      double requestedMinimum, double requestedMaximum, double safetyLimit) {
        double limit = Math.max(1.0D, Math.abs(safetyLimit));
        double minimum = -limit;
        double maximum = limit;
        if (preventWindup) {
            minimum = finiteOr(requestedMinimum, minimum);
            maximum = finiteOr(requestedMaximum, maximum);
            if (minimum > maximum) {
                double swap = minimum;
                minimum = maximum;
                maximum = swap;
            }
            minimum = clamp(minimum, -limit, limit);
            maximum = clamp(maximum, -limit, limit);
        }

        double next = previous + error;
        if (Double.isNaN(next)) {
            next = 0.0D;
        } else if (next == Double.POSITIVE_INFINITY) {
            next = maximum;
        } else if (next == Double.NEGATIVE_INFINITY) {
            next = minimum;
        }
        return clamp(next, minimum, maximum);
    }

    // Use the fallback when the value is not finite
    private static double finiteOr(double val, double fallback) {
        return Double.isFinite(val) ? val : fallback;
    }

    // Clamp the PID controller math
    private static double clamp(double val, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, val));
    }
}
