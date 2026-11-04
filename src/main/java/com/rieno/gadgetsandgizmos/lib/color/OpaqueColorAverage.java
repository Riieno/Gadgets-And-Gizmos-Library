package com.rieno.gadgetsandgizmos.lib.color;

import java.util.function.IntUnaryOperator;

// Average opaque NativeImage ABGR pixels without filling transparent texture gaps
public final class OpaqueColorAverage{
    private OpaqueColorAverage(){}

    public static int rgb(int count, IntUnaryOperator pixels){
        if(count <= 0) return -1;
        long red = 0, green = 0, blue = 0;
        for(int idx = 0; idx < count; idx++){
            int color = pixels.applyAsInt(idx);
            if((color >>> 24) != 255) return -1;
            red += color & 255; green += color >> 8 & 255; blue += color >> 16 & 255;
        }
        return (int)(red / count) << 16 | (int)(green / count) << 8 | (int)(blue / count);
    }
}
