package com.rieno.gadgetsandgizmos.lib.color;

// Compose solid colors, gradients and style shading before sampling geometry
@FunctionalInterface
public interface ColorPaint{
    int sample(double x);

    static ColorPaint solid(int color){ return new Solid(color); }

    static ColorPaint gradient(ColorRamp ramp, double width){
        return x -> ramp.sample((x - 0.5) / Math.max(1, width - 1));
    }

    // Keep solid drawing on its original path and defer gradient shading until the final sample
    static ColorPaint mix(ColorPaint first, ColorPaint last, double amount){
        double val = Double.isFinite(amount) ? Math.clamp(amount, 0, 1) : 0;
        if(first instanceof Solid a && last instanceof Solid b) return solid(blend(a.color(), b.color(), val));
        return x -> blend(first.sample(x), last.sample(x), val);
    }

    static ColorPaint alpha(ColorPaint paint, double amount){
        double val = Double.isFinite(amount) ? Math.clamp(amount, 0, 1) : 0;
        if(paint instanceof Solid solid) return solid(opacity(solid.color(), val));
        return x -> opacity(paint.sample(x), val);
    }

    private static int opacity(int color, double amount){
        return (int)Math.round((color >>> 24) * amount) << 24 | color & 0xFFFFFF;
    }

    private static int blend(int first, int last, double amount){
        int res = 0;
        for(int shift = 0; shift <= 24; shift += 8){
            int a = first >>> shift & 255, b = last >>> shift & 255;
            res |= (int)Math.round(a + (b - a) * amount) << shift;
        }
        return res;
    }

    record Solid(int color) implements ColorPaint{
        @Override public int sample(double x){ return color; }
    }
}
