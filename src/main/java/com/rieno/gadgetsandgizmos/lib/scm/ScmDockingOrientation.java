package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.world.phys.Vec3;

// Score a ship connector by the body rotation needed to face a docking connector
public final class ScmDockingOrientation{
    private ScmDockingOrientation(){}

    // Prefer a matching ship-relative inclination before choosing the nearest yaw
    public static double score(Vec3 connectorFacing, Vec3 connectorUp,
                               Vec3 shipUp, Vec3 targetFacing, Vec3 targetUp){
        Vec3 facing = unit(connectorFacing);
        Vec3 up = unit(connectorUp);
        Vec3 bodyUp = unit(shipUp);
        Vec3 target = unit(targetFacing);
        Vec3 dockUp = unit(targetUp);
        if(facing.lengthSqr() == 0.0D || bodyUp.lengthSqr() == 0.0D
                || target.lengthSqr() == 0.0D) return Double.NEGATIVE_INFINITY;
        double shipInclination = facing.dot(bodyUp);
        double targetInclination = target.y;
        return -4.0D * Math.abs(shipInclination - targetInclination)
                + 0.5D * facing.dot(target) + 0.25D * up.dot(dockUp);
    }

    // Keep a turn axis when the connector faces exactly away from the dock
    public static Vec3 facingError(Vec3 connectorFacing, Vec3 connectorUp,
                                   Vec3 targetFacing){
        Vec3 facing = unit(connectorFacing);
        Vec3 target = unit(targetFacing);
        if(facing.lengthSqr() == 0.0D || target.lengthSqr() == 0.0D)
            return Vec3.ZERO;
        Vec3 error = facing.cross(target);
        if(facing.dot(target) < -0.98D && error.lengthSqr() < 1.0E-4D){
            Vec3 up = unit(connectorUp);
            error = up.subtract(facing.scale(up.dot(facing)));
            if(error.lengthSqr() > 1.0E-12D) return error.normalize();
        }
        return error;
    }

    private static Vec3 unit(Vec3 value){
        if(value == null || !Double.isFinite(value.x)
                || !Double.isFinite(value.y) || !Double.isFinite(value.z)
                || value.lengthSqr() <= 1.0E-12D) return Vec3.ZERO;
        return value.normalize();
    }
}
