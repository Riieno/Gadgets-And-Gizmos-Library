package com.rieno.gadgetsandgizmos.lib.color;

// Read and replace ARGB opacity without changing RGB channels
public final class ColorOpacity{
    private ColorOpacity(){}

    // Get normalized opacity from an ARGB color
    public static float opacity(int color){
        return (color >>> 24 & 0xFF) / 255.0F;
    }

    // Replace opacity while preserving the RGB channels
    public static int withOpacity(int color, float opacity){
        float val = Float.isFinite(opacity) ? Math.clamp(opacity, 0.0F, 1.0F) : 0.0F;
        return Math.round(val * 255.0F) << 24 | color & 0x00FFFFFF;
    }
}
