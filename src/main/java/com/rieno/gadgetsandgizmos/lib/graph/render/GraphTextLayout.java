package com.rieno.gadgetsandgizmos.lib.graph.render;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.function.ToIntFunction;

// Fit graph text inside a measured drawing width
public final class GraphTextLayout {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Keep the graph text helpers static
    private GraphTextLayout() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Shorten text to fit the measured width while preserving whole characters
    public static String ellipsize(String val, ToIntFunction<String> measure, int width) {
        if (val == null || val.isEmpty() || measure == null || width <= 0) return "";
        String text = val.replace('\r', ' ').replace('\n', ' ');
        if (measure.applyAsInt(text) <= width) return text;

        String suffix = "\u2026";
        if (measure.applyAsInt(suffix) > width) return "";

        int low = 0;
        int high = text.codePointCount(0, text.length());
        while (low < high) {
            int middle = (low + high + 1) / 2;
            int end = text.offsetByCodePoints(0, middle);
            if (measure.applyAsInt(text.substring(0, end) + suffix) <= width) low = middle;
            else high = middle - 1;
        }
        return text.substring(0, text.offsetByCodePoints(0, low)) + suffix;
    }
}
