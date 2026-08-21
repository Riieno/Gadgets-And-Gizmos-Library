package com.rieno.gadgetsandgizmos.lib.display;

// Share fitted widget geometry and inverse pointer transforms between renderers and hosts
public final class DisplayWidgetProjection {
    private DisplayWidgetProjection() {
    }

    // Fit an untransformed widget rectangle to a surface
    public static Bounds fit(int x, int y, int width, int height,
                             int surfaceWidth, int surfaceHeight) {
        int fittedWidth = Math.max(1, Math.min(width, Math.max(1, surfaceWidth)));
        int fittedHeight = Math.max(1, Math.min(height, Math.max(1, surfaceHeight)));
        int fittedX = clamp(x, 0, Math.max(0, surfaceWidth - fittedWidth));
        int fittedY = clamp(y, 0, Math.max(0, surfaceHeight - fittedHeight));
        return new Bounds(fittedX, fittedY, fittedWidth, fittedHeight);
    }

    // Get the greatest scale whose rotated rectangle remains on the surface
    public static double maximumScale(Bounds bounds, int surfaceWidth,
                                      int surfaceHeight, double rotationDegrees) {
        double radians = Math.toRadians(rotationDegrees);
        double cosine = Math.abs(Math.cos(radians));
        double sine = Math.abs(Math.sin(radians));
        double rotatedWidth = cosine * bounds.width() + sine * bounds.height();
        double rotatedHeight = sine * bounds.width() + cosine * bounds.height();
        double centerX = bounds.x() + bounds.width() * 0.5D;
        double centerY = bounds.y() + bounds.height() * 0.5D;
        double horizontal = rotatedWidth <= 0.0D ? 1.0D
                : Math.max(0.01D, 2.0D * Math.min(
                centerX, surfaceWidth - centerX) / rotatedWidth);
        double vertical = rotatedHeight <= 0.0D ? 1.0D
                : Math.max(0.01D, 2.0D * Math.min(
                centerY, surfaceHeight - centerY) / rotatedHeight);
        return Math.max(0.01D, Math.min(1.0D,
                Math.min(horizontal, vertical)));
    }

    // Convert a surface pointer into the widget's unscaled, unrotated local coordinates
    public static Point unproject(Bounds bounds, double pointerX, double pointerY,
                                  double scale, double rotationDegrees) {
        double safeScale = Math.max(0.01D, Math.abs(scale));
        double centerX = bounds.x() + bounds.width() * 0.5D;
        double centerY = bounds.y() + bounds.height() * 0.5D;
        double dx = pointerX - centerX;
        double dy = pointerY - centerY;
        double radians = Math.toRadians(rotationDegrees);
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        double localX = (dx * cosine + dy * sine) / safeScale
                + bounds.width() * 0.5D;
        double localY = (-dx * sine + dy * cosine) / safeScale
                + bounds.height() * 0.5D;
        return new Point(localX, localY);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record Bounds(int x, int y, int width, int height) {
    }

    public record Point(double x, double y) {
        public boolean isInside(Bounds bounds) {
            return x >= 0.0D && y >= 0.0D
                    && x <= bounds.width() && y <= bounds.height();
        }

        public double horizontalFraction(Bounds bounds) {
            return Math.max(0.0D, Math.min(1.0D,
                    x / Math.max(1.0D, bounds.width())));
        }
    }
}
