package com.rieno.gadgetsandgizmos.lib.color;

import java.util.List;

// Retain ordered ARGB samples including intermediate stops and opacity
public record ColorRamp(List<Integer> colors){
    public ColorRamp{
        colors = List.copyOf(colors);
        if(colors.isEmpty() || colors.size() > 256) throw new IllegalArgumentException("A color ramp requires 1 to 256 samples");
    }

    // Interpolate all four channels across the complete ramp
    public int sample(double position){
        double idx = (Double.isFinite(position) ? Math.clamp(position, 0, 1) : 0) * (colors.size() - 1);
        int first = (int)idx, last = Math.min(colors.size() - 1, first + 1);
        double amount = idx - first;
        int res = 0;
        for(int shift = 0; shift <= 24; shift += 8){
            int a = colors.get(first) >>> shift & 255, b = colors.get(last) >>> shift & 255;
            res |= (int)Math.round(a + (b - a) * amount) << shift;
        }
        return res;
    }
}
