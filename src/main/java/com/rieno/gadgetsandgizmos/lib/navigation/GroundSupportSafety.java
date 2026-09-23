package com.rieno.gadgetsandgizmos.lib.navigation;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.function.DoublePredicate;

/** Bound forward ground travel by the first sustained unsupported terrain span. */
public final class GroundSupportSafety {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ground support helper
    private GroundSupportSafety() {
    }

    /** Return the supported prefix while allowing short wheelbase-scale gaps. */
    public static double supportedDistance(
            double maximumDistance,
            double sampleSpacing,
            double maximumUnsupportedSpan,
            DoublePredicate supportAtDistance
    ) {
        double maximum = Math.max(0.0D, finite(maximumDistance));
        if (maximum <= 1.0E-9D || supportAtDistance == null) return maximum;
        double spacing = Math.max(0.25D, finitePositive(sampleSpacing, 0.75D));
        double permittedGap = Math.max(0.0D, finite(maximumUnsupportedSpan));
        double unsupportedStart = Double.NaN;
        for (double distance = Math.min(spacing, maximum);
             distance <= maximum + 1.0E-8D;
             distance = Math.min(maximum, distance + spacing)) {
            boolean supported = supportAtDistance.test(distance);
            if (supported) {
                unsupportedStart = Double.NaN;
            } else if (!Double.isFinite(unsupportedStart)) {
                unsupportedStart = Math.max(0.0D, distance - spacing);
            } else if (distance - unsupportedStart > permittedGap + 1.0E-8D) {
                return unsupportedStart;
            }
            if (distance >= maximum) break;
        }
        return Double.isFinite(unsupportedStart)
                && maximum - unsupportedStart > permittedGap + 1.0E-8D
                ? unsupportedStart : maximum;
    }

    // Normalize one finite scalar
    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0D;
    }

    // Normalize one positive finite scalar
    private static double finitePositive(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0D ? value : fallback;
    }
}
