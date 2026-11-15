package com.rieno.gadgetsandgizmos.lib.display;

// Resolve authored content into surface pixels before applying rotation and scale
public record DisplayContentLayout(float x, float y, int width, int height, float scale, float rotation){
    public static DisplayContentLayout resolve(double x, double y, double width, double height, double scale, double rotation,
                                               double canvasWidth, double canvasHeight, int surfaceWidth, int surfaceHeight){
        return new DisplayContentLayout((float)(x / canvasWidth * surfaceWidth), (float)(y / canvasHeight * surfaceHeight),
                Math.max(1, (int)Math.round(width / canvasWidth * surfaceWidth)), Math.max(1, (int)Math.round(height / canvasHeight * surfaceHeight)),
                (float)Math.clamp(scale, .01, 100), (float)rotation);
    }

    public static DisplayContentLayout fill(int width, int height){ return new DisplayContentLayout(0, 0, Math.max(1, width), Math.max(1, height), 1, 0); }

    // Return normalized content coordinates using the same pixel center as the renderer
    public double[] point(double px, double py){
        if(!Double.isFinite(px) || !Double.isFinite(py)) return null;
        double dx = px - (x + width * .5), dy = py - (y + height * .5);
        double angle = Math.toRadians(rotation), cosine = Math.cos(angle), sine = Math.sin(angle);
        double localX = (dx * cosine + dy * sine) / scale + width * .5;
        double localY = (-dx * sine + dy * cosine) / scale + height * .5;
        return localX < 0 || localX >= width || localY < 0 || localY >= height ? null : new double[]{localX / width, localY / height};
    }
}
