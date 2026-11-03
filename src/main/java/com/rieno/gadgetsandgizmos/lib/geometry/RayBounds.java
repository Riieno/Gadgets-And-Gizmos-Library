package com.rieno.gadgetsandgizmos.lib.geometry;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

// Clip finite ray segments while preserving rays which begin inside a volume
public final class RayBounds{
    private RayBounds(){}

    // Retain only the portion which can reach geometry in the supplied bounds
    public static @Nullable Segment clip(AABB bounds, Vec3 from, Vec3 to){
        double first = 0, last = 1;
        for(int axis = 0; axis < 3; axis++){
            double start = axis == 0 ? from.x : axis == 1 ? from.y : from.z;
            double end = axis == 0 ? to.x : axis == 1 ? to.y : to.z;
            double min = axis == 0 ? bounds.minX : axis == 1 ? bounds.minY : bounds.minZ;
            double max = axis == 0 ? bounds.maxX : axis == 1 ? bounds.maxY : bounds.maxZ;
            double delta = end - start;
            if(!Double.isFinite(start) || !Double.isFinite(end)) return null;
            if(delta == 0){
                if(start < min || start > max) return null;
                continue;
            }
            double near = (min - start) / delta, far = (max - start) / delta;
            first = Math.max(first, Math.min(near, far));
            last = Math.min(last, Math.max(near, far));
            if(first > last) return null;
        }
        return new Segment(from.lerp(to, first), from.lerp(to, last));
    }

    // Carry endpoints in the caller's original coordinate space
    public record Segment(Vec3 from, Vec3 to){}
}
