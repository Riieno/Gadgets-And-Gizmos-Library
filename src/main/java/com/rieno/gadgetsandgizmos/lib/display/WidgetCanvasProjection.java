package com.rieno.gadgetsandgizmos.lib.display;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

// Describe a widget canvas independently of a display's physical dimensions
public final class WidgetCanvasProjection{
    private WidgetCanvasProjection(){}

    public static Layout layout(boolean hologram, int canvasWidth, int canvasHeight,
                                int surfaceWidth, int surfaceHeight, double sizeX, double sizeY,
                                double offsetX, double offsetY, double offsetZ, double rotation,
                                int pixelsPerBlock){
        double width = hologram ? finite(sizeX, 1) * pixelsPerBlock : surfaceWidth;
        double height = hologram ? finite(sizeY, 1) * pixelsPerBlock : surfaceHeight;
        return new Layout(hologram ? finite(offsetX, 0) * pixelsPerBlock : 0,
                hologram ? -finite(offsetY, 0) * pixelsPerBlock : 0,
                hologram ? -finite(offsetZ, 0) * pixelsPerBlock : 0,
                Math.max(1, width), Math.max(1, height), Math.max(1, canvasWidth),
                Math.max(1, canvasHeight), hologram ? finite(rotation, 0) : 0);
    }

    // Intersect a world ray with the projected plane; right and down must be unit vectors
    public static @Nullable Hit intersect(Vec3 eye, Vec3 direction, double reach, Vec3 topLeft,
                                          Vec3 right, Vec3 down, Layout layout, int pixelsPerBlock){
        Vec3 normal = right.cross(down);
        Vec3 plane = topLeft.add(normal.scale(layout.z() / pixelsPerBlock));
        double denominator = direction.dot(normal);
        if(Math.abs(denominator) < 1.0E-8) return null;
        double distance = plane.subtract(eye).dot(normal) / denominator;
        if(!Double.isFinite(distance) || distance < 0 || distance > reach) return null;
        Vec3 relative = eye.add(direction.scale(distance)).subtract(plane);
        double x = relative.dot(right) * pixelsPerBlock;
        double y = relative.dot(down) * pixelsPerBlock;
        DisplayWidgetProjection.Point point = layout.unproject(x, y);
        return new Hit(distance, point.x(), point.y());
    }

    // Resize authored element bounds while retaining their proportions
    public static void resizeCanvas(net.minecraft.nbt.CompoundTag data, int width, int height){
        int oldWidth = Math.max(1, data.getInt("WidgetWidth"));
        int oldHeight = Math.max(1, data.getInt("WidgetHeight"));
        width = Math.max(1, width); height = Math.max(1, height);
        if(width == oldWidth && height == oldHeight) return;
        net.minecraft.nbt.ListTag elements = data.getList("WidgetElements", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for(net.minecraft.nbt.Tag raw : elements){
            net.minecraft.nbt.CompoundTag elm = (net.minecraft.nbt.CompoundTag)raw;
            elm.putInt("X", (int)Math.round(elm.getInt("X") * (double)width / oldWidth));
            elm.putInt("Y", (int)Math.round(elm.getInt("Y") * (double)height / oldHeight));
            elm.putInt("W", Math.max(1, (int)Math.round(elm.getInt("W") * (double)width / oldWidth)));
            elm.putInt("H", Math.max(1, (int)Math.round(elm.getInt("H") * (double)height / oldHeight)));
        }
        data.putInt("WidgetWidth", width); data.putInt("WidgetHeight", height);
    }

    private static double finite(double value, double fallback){
        return Double.isFinite(value) ? value : fallback;
    }

    public record Layout(double x, double y, double z, double width, double height,
                         int canvasWidth, int canvasHeight, double rotation){
        public double scaleX(){ return width / canvasWidth; }
        public double scaleY(){ return height / canvasHeight; }
        public DisplayWidgetProjection.Point unproject(double pointerX, double pointerY){
            double dx = pointerX - x - width / 2;
            double dy = pointerY - y - height / 2;
            double radians = Math.toRadians(rotation);
            return new DisplayWidgetProjection.Point(
                    (dx * Math.cos(radians) + dy * Math.sin(radians) + width / 2) / scaleX(),
                    (-dx * Math.sin(radians) + dy * Math.cos(radians) + height / 2) / scaleY());
        }
    }
    public record Hit(double distance, double x, double y){}
}
