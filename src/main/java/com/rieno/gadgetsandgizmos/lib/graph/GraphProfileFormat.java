package com.rieno.gadgetsandgizmos.lib.graph;

import java.util.Locale;

// Share timing labels and absolute per-instruction performance bands
public final class GraphProfileFormat{
    private GraphProfileFormat(){}

    public static String micros(double nanos){ return String.format(Locale.ROOT, "%.2f \u03bcs", nanos / 1000.0D); }

    public static int color(double nanos){
        if(nanos < 10_000) return 0xFF80E09A;
        if(nanos < 100_000) return 0xFFFFD166;
        return 0xFFFF7878;
    }
}
