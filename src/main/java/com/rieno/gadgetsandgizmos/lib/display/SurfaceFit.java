package com.rieno.gadgetsandgizmos.lib.display;

// Share the drawn media rectangle and its inverse pointer mapping
public record SurfaceFit(double x, double y, double width, double height, double scaleX, double scaleY){
    public static SurfaceFit fit(double sourceWidth, double sourceHeight, double width, double height, boolean preserveAspect){
        if(!Double.isFinite(sourceWidth) || !Double.isFinite(sourceHeight) || !Double.isFinite(width) || !Double.isFinite(height)
                || sourceWidth <= 0 || sourceHeight <= 0 || width <= 0 || height <= 0) throw new IllegalArgumentException("Surface dimensions must be positive and finite");
        double sx = width / sourceWidth, sy = height / sourceHeight;
        if(preserveAspect) sx = sy = Math.min(sx, sy);
        double w = sourceWidth * sx, h = sourceHeight * sy;
        return new SurfaceFit((width - w) / 2, (height - h) / 2, w, h, sx, sy);
    }

    // Exclude letterboxing instead of clamping it onto the edge of the media
    public double[] point(double px, double py){
        if(!Double.isFinite(px) || !Double.isFinite(py)) return null;
        double nx = (px - x) / width, ny = (py - y) / height;
        return nx < 0 || nx >= 1 || ny < 0 || ny >= 1 ? null : new double[]{nx, ny};
    }
}
