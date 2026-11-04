package com.rieno.gadgetsandgizmos.lib.color;

import java.util.function.IntUnaryOperator;

// Average texture pixels with alpha, then compose opaque base faces and transparent overlays
public final class TextureColorAverage{
    private TextureColorAverage(){}

    public static int argb(int count, IntUnaryOperator pixels){
        if(count <= 0) return 0;
        long red = 0, green = 0, blue = 0, alpha = 0;
        for(int idx = 0; idx < count; idx++){
            int color = pixels.applyAsInt(idx), opacity = color >>> 24;
            alpha += opacity;
            red += (long)(color & 255) * opacity;
            green += (long)(color >> 8 & 255) * opacity;
            blue += (long)(color >> 16 & 255) * opacity;
        }
        if(alpha == 0) return 0;
        return (int)(alpha / count) << 24 | (int)(red / alpha) << 16 | (int)(green / alpha) << 8 | (int)(blue / alpha);
    }

    public static int over(int foreground, int background){
        int front = foreground >>> 24, back = background >>> 24;
        if(front == 0) return background;
        if(front == 255) return foreground;
        int opacity = front * 255 + back * (255 - front);
        if(opacity == 0) return 0;
        int color = (opacity + 127) / 255 << 24;
        for(int shift : new int[]{0, 8, 16}){
            int channel = ((foreground >> shift & 255) * front * 255 + (background >> shift & 255) * back * (255 - front) + opacity / 2) / opacity;
            color |= channel << shift;
        }
        return color;
    }
}
