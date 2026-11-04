package com.rieno.gadgetsandgizmos.lib.display;

import net.minecraft.nbt.CompoundTag;

// Resolve element transforms in authored canvas units before any surface or preview scaling
public final class WidgetElementLayout{
    private WidgetElementLayout(){}

    public static Element resolve(CompoundTag data, int canvasWidth, int canvasHeight, boolean constrain){
        var bounds = new DisplayWidgetProjection.Bounds(data.getInt("X"), data.getInt("Y"),
                Math.max(1, data.getInt("W")), Math.max(1, data.getInt("H")));
        double rotation = finite(data.getDouble("Rotation"), 0);
        double scale = data.contains("Scale") ? finite(data.getDouble("Scale"), 1) : 1;
        scale = Math.clamp(scale, 0.01, 100);
        if(constrain){
            bounds = DisplayWidgetProjection.fit(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                    canvasWidth, canvasHeight);
            scale = Math.min(scale, DisplayWidgetProjection.maximumScale(bounds, canvasWidth, canvasHeight, rotation));
        }
        return new Element(bounds, scale, rotation);
    }

    private static double finite(double val, double fallback){ return Double.isFinite(val) ? val : fallback; }

    public record Element(DisplayWidgetProjection.Bounds bounds, double scale, double rotation){
        // Map pointer coordinates through the same transform used to draw this element
        public DisplayWidgetProjection.Point unproject(double x, double y){
            return DisplayWidgetProjection.unproject(bounds, x, y, scale, rotation);
        }

        public DisplayWidgetProjection.Point project(double x, double y){
            double dx = (x - bounds.width() * 0.5) * scale;
            double dy = (y - bounds.height() * 0.5) * scale;
            double radians = Math.toRadians(rotation);
            return new DisplayWidgetProjection.Point(bounds.x() + bounds.width() * 0.5
                    + dx * Math.cos(radians) - dy * Math.sin(radians), bounds.y() + bounds.height() * 0.5
                    + dx * Math.sin(radians) + dy * Math.cos(radians));
        }
    }
}
