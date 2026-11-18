package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.world.phys.Vec3;
import com.rieno.gadgetsandgizmos.lib.navigation.SplineConstraintFrame;
import org.joml.Quaterniond;
import org.joml.Vector3d;

// Score a ship connector by the body rotation needed to face a docking connector
public final class ScmDockingOrientation{
    public static final double ROLL_STEP_DEGREES = 45.0D;
    private ScmDockingOrientation(){}

    // Keep the nearest relative roll while aligning the mating faces
    public static Vec3 alignedDirection(Vec3 direction, Vec3 facing, Vec3 up, Vec3 targetFacing, Vec3 targetUp, double rollStepDegrees){
        return alignedDirection(direction, facing, up, targetFacing,
                nearestTargetUp(facing, up, targetFacing, targetUp, rollStepDegrees));
    }

    // Measure the shortest correction to a stepped connector frame
    public static Vec3 alignmentError(Vec3 facing, Vec3 up, Vec3 targetFacing, Vec3 targetUp, double rollStepDegrees){
        return alignmentError(facing, up, targetFacing,
                nearestTargetUp(facing, up, targetFacing, targetUp, rollStepDegrees));
    }

    // Select an equivalent roll in the target connector's live coordinate frame
    public static Vec3 nearestTargetUp(Vec3 facing, Vec3 up, Vec3 targetFacing, Vec3 targetUp, double rollStepDegrees){
        double steps = 360.0D / rollStepDegrees;
        if(!Double.isFinite(steps) || steps < 1 || steps > 360 || Math.abs(steps - Math.rint(steps)) > 1.0E-8D){
            throw new IllegalArgumentException("The roll step must divide 360 degrees into at most 360 steps");
        }
        if(unit(facing).lengthSqr() == 0 || unit(targetFacing).lengthSqr() == 0) return unit(targetUp);
        var current = SplineConstraintFrame.orientation(facing, up);
        var target = SplineConstraintFrame.orientation(targetFacing, targetUp);
        var relative = new Quaterniond(target).conjugate().mul(current);
        double step = Math.toRadians(rollStepDegrees);
        double roll = Math.rint(2.0D * Math.atan2(relative.z, relative.w) / step) * step;
        var snapped = target.rotateZ(roll).transform(new Vector3d(0, 1, 0));
        return new Vec3(snapped.x, snapped.y, snapped.z);
    }

    // Rotate a vehicle heading by the connector's complete facing and roll correction
    public static Vec3 alignedDirection(Vec3 direction, Vec3 facing, Vec3 up, Vec3 targetFacing, Vec3 targetUp){
        Vec3 error = alignmentError(facing, up, targetFacing, targetUp);
        double angle = error.length();
        if(angle <= 1.0E-12D) return unit(direction);
        Vec3 axis = error.scale(1 / angle);
        double cos = Math.cos(angle);
        return unit(direction.scale(cos).add(axis.cross(direction).scale(Math.sin(angle)))
                .add(axis.scale(axis.dot(direction) * (1 - cos))));
    }

    // Resolve facing and connector roll as one shortest rotation instead of competing cross products
    public static Vec3 alignmentError(Vec3 facing, Vec3 up, Vec3 targetFacing, Vec3 targetUp){
        if(unit(facing).lengthSqr() == 0 || unit(targetFacing).lengthSqr() == 0) return Vec3.ZERO;
        var current = SplineConstraintFrame.orientation(facing, up);
        var target = SplineConstraintFrame.orientation(targetFacing, targetUp);
        var error = target.mul(current.conjugate()).normalize();
        if(error.w < 0) error.mul(-1.0D);
        double sin = Math.sqrt(error.x * error.x + error.y * error.y + error.z * error.z);
        if(sin < 1.0E-9D) return Vec3.ZERO;
        double angle = 2.0D * Math.atan2(sin, Math.max(0, error.w));
        return new Vec3(error.x, error.y, error.z).scale(angle / sin);
    }

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
