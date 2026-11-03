package com.rieno.gadgetsandgizmos.lib.geometry;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

import java.util.function.UnaryOperator;

// Share a rectangular screen's corners between rendering and pointer projection
public record PlanarSurface(Vec3 topLeft, Vec3 bottomLeft, Vec3 bottomRight, Vec3 topRight){
    // Transform every corner while preserving its texture coordinates
    public PlanarSurface transform(UnaryOperator<Vec3> transform){
        return new PlanarSurface(transform.apply(topLeft), transform.apply(bottomLeft),
                transform.apply(bottomRight), transform.apply(topRight));
    }

    // Rotate the screen about the same pivot as its model
    public PlanarSurface rotate(Quaterniondc rotation, Vec3 pivot){
        return transform(point -> {
            Vector3d val = new Vector3d(point.x - pivot.x, point.y - pivot.y, point.z - pivot.z);
            rotation.transform(val);
            return new Vec3(val.x + pivot.x, val.y + pivot.y, val.z + pivot.z);
        });
    }

    // Get the outward normal from the screen's texture winding
    public Vec3 normal(){
        return bottomLeft.subtract(topLeft).cross(topRight.subtract(topLeft)).normalize();
    }

    // Project a point onto the screen and reject positions outside its border
    public @Nullable Point project(Vec3 point){
        Vec3 horizontal = topRight.subtract(topLeft);
        Vec3 vertical = bottomLeft.subtract(topLeft);
        Vec3 offset = point.subtract(topLeft);
        double u = offset.dot(horizontal) / horizontal.lengthSqr();
        double v = offset.dot(vertical) / vertical.lengthSqr();
        double tolerance = 0.015D;
        if(!Double.isFinite(u) || !Double.isFinite(v)
                || u < -tolerance || u > 1.0D + tolerance
                || v < -tolerance || v > 1.0D + tolerance) return null;
        return new Point(Math.clamp(u, 0.0D, 1.0D), Math.clamp(v, 0.0D, 1.0D));
    }

    // Intersect a finite pointer ray with the front of the screen
    public @Nullable Point intersect(Vec3 start, Vec3 end){
        Vec3 delta = end.subtract(start);
        Vec3 normal = normal();
        double denominator = delta.dot(normal);
        if(denominator >= -1.0E-8D) return null;
        double distance = topLeft.subtract(start).dot(normal) / denominator;
        if(!Double.isFinite(distance) || distance < 0.0D || distance > 1.0D) return null;
        return project(start.add(delta.scale(distance)));
    }

    // Store normalized screen coordinates
    public record Point(double x, double y){}
}
