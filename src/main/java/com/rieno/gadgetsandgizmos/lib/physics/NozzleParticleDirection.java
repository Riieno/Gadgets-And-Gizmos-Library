package com.rieno.gadgetsandgizmos.lib.physics;

import net.minecraft.world.phys.Vec3;

/** Tilt a particle plume toward a nozzle's upper face without changing force geometry. */
public final class NozzleParticleDirection {
    private NozzleParticleDirection() {}

    public static Vec3 tiltToward(Vec3 outward, Vec3 upperFace, double degrees) {
        if (outward == null || upperFace == null || !Double.isFinite(degrees)
                || outward.lengthSqr() < 1.0E-12D) return Vec3.ZERO;
        Vec3 axis = outward.normalize();
        Vec3 upward = upperFace.subtract(axis.scale(upperFace.dot(axis)));
        if (upward.lengthSqr() < 1.0E-12D) return axis;
        double radians = Math.toRadians(degrees);
        return axis.scale(Math.cos(radians)).add(upward.normalize().scale(Math.sin(radians))).normalize();
    }
}
