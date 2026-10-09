package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.world.phys.Vec3;

import java.util.List;

// Describe directional thrust capacity without replacing a provider's force allocator
public final class ScmThrustGeometry{
    private ScmThrustGeometry(){}

    // Get the maximum signed projection available inside a bounded thrust cone
    public static double maximumProjection(Vec3 force, double coneDegrees, Vec3 axis){
        if(!valid(force) || !valid(axis)) return 0.0D;
        double thrust = force.length();
        double length = axis.length();
        if(thrust <= 1.0E-12D || length <= 1.0E-12D) return 0.0D;
        double cosine = Math.clamp(force.dot(axis) / (thrust * length), -1.0D, 1.0D);
        double angle = Math.acos(cosine);
        double cone = Math.toRadians(coneDegrees(coneDegrees));
        return thrust * length * Math.max(0.0D, Math.cos(Math.max(0.0D, angle - cone)));
    }

    // Supply the two steering directions for linearizing a vector provider's response
    public static List<Vec3> steeringForces(Vec3 force, double coneDegrees){
        if(!valid(force)) return List.of();
        double lateral = force.length() * Math.sin(Math.toRadians(coneDegrees(coneDegrees)));
        if(lateral <= 1.0E-12D) return List.of();
        Vec3 normal = force.normalize();
        Vec3 ref = Math.abs(normal.y) < 0.9D ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 first = normal.cross(ref).normalize();
        return List.of(first.scale(lateral), normal.cross(first).normalize().scale(lateral));
    }

    // Keep geometry consistent with forward-only vector providers
    public static double coneDegrees(double val){
        return Double.isFinite(val) ? Math.clamp(val, 0.0D, 90.0D) : 0.0D;
    }

    private static boolean valid(Vec3 val){
        return val != null && Double.isFinite(val.x) && Double.isFinite(val.y) && Double.isFinite(val.z);
    }
}
