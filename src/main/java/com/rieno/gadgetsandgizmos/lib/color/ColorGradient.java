package com.rieno.gadgetsandgizmos.lib.color;

// Interpolate an RGB gradient through three colors
public record ColorGradient(int startColor, int middleColor, int endColor){
    // Keep colors within the RGB range
    public ColorGradient{
        startColor &= 0xFFFFFF;
        middleColor &= 0xFFFFFF;
        endColor &= 0xFFFFFF;
    }

    // Sample the gradient from its start to its end
    public int sample(float progress){
        return sample(progress, 0.5F, 1.0F);
    }

    // Sample custom color stops and retain the end color afterwards
    public int sample(float progress, float middleProgress, float endProgress){
        if(!Float.isFinite(middleProgress) || !Float.isFinite(endProgress)
                || middleProgress <= 0.0F || middleProgress >= endProgress || endProgress > 1.0F){
            throw new IllegalArgumentException("Gradient stops must satisfy 0 < middle < end <= 1");
        }
        float val = clamp(progress);
        if(val >= endProgress) return endColor;
        return val <= middleProgress ? blend(startColor, middleColor, val / middleProgress)
                : blend(middleColor, endColor, (val - middleProgress) / (endProgress - middleProgress));
    }

    // Blend two RGB colors without changing their opacity
    public static int blend(int from, int to, float ratio){
        float val = clamp(ratio);
        int red = channel(from >> 16 & 0xFF, to >> 16 & 0xFF, val);
        int green = channel(from >> 8 & 0xFF, to >> 8 & 0xFF, val);
        int blue = channel(from & 0xFF, to & 0xFF, val);
        return red << 16 | green << 8 | blue;
    }

    // Pack normalized color channels into RGB
    public static int rgb(float red, float green, float blue){
        return Math.round(clamp(red) * 255.0F) << 16
                | Math.round(clamp(green) * 255.0F) << 8
                | Math.round(clamp(blue) * 255.0F);
    }

    // Interpolate one color channel
    private static int channel(int from, int to, float ratio){
        return Math.round(from + (to - from) * ratio);
    }

    // Keep normalized values finite and within range
    private static float clamp(float val){
        return Float.isFinite(val) ? Math.clamp(val, 0.0F, 1.0F) : 0.0F;
    }
}
