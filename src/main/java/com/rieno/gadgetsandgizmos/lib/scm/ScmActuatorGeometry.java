package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

// Resolve actuator geometry from declared limits without commanding the physical provider
public final class ScmActuatorGeometry{
    private ScmActuatorGeometry(){}

    // Invert the native tangent gimbal basis used by gimbalDirection
    public static Vec3 gimbalCoordinates(Vec3 forward, Vec3 force, double halfAngle){
        Vec3 axis = forward.normalize();
        double axial = force.dot(axis);
        if(axial <= 1.0E-9D) return Vec3.ZERO;
        Vec3 reference = Math.abs(axis.y) > 0.999D ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
        Vec3 right = axis.cross(reference).normalize();
        if(Math.abs(axis.y) > 0.999D) right = right.scale(-1.0D);
        Vec3 up = right.cross(axis).normalize();
        double denominator = axial * Math.tan(Math.toRadians(halfAngle));
        if(denominator <= 1.0E-9D) return Vec3.ZERO;
        return new Vec3(Math.clamp(-force.dot(right) / denominator, -1.0D, 1.0D),
                Math.clamp(-force.dot(up) / denominator, -1.0D, 1.0D), 0.0D);
    }

    // Rotate a response around an actuator's host-local pivot in the root frame
    public static Vec3 position(ScmGeometryFrame frame, Quaterniondc rotation, Vec3 point){
        return frame.origin().add(direction(frame, rotation, point.subtract(frame.origin())));
    }

    public static Vec3 direction(ScmGeometryFrame frame, Quaterniondc rotation, Vec3 direction){
        Vector3d local = new Vector3d(direction.dot(frame.xAxis()), direction.dot(frame.yAxis()), direction.dot(frame.zAxis()));
        rotation.transform(local);
        return frame.xAxis().scale(local.x).add(frame.yAxis().scale(local.y)).add(frame.zAxis().scale(local.z));
    }

    // Preserve shaft roll while changing only the head's tilt
    public static Quaterniond tiltDelta(Vec3 current, Vec3 target){
        return new Quaterniond().rotationTo(vector(current.normalize()), vector(target.normalize()));
    }

    // Preserve the provider's neutral-frame roll when predicting a mounted head pose
    public static Quaterniond tiltDelta(Vec3 neutral, Vec3 current, Vec3 target){
        Quaterniond from = tiltDelta(neutral, current);
        return tiltDelta(neutral, target).mul(from.invert());
    }

    public static Quaterniond rotaryDelta(Vec3 axis, double currentDegrees, double targetDegrees){
        if(axis.lengthSqr() <= 1.0E-12D) return new Quaterniond();
        Vec3 normal = axis.normalize();
        return new Quaterniond().rotationAxis(Math.toRadians(targetDegrees - currentDegrees), normal.x, normal.y, normal.z);
    }

    // Resolve a nozzle with two native tangent-space steering coordinates
    public static Vec3 gimbalDirection(Vec3 forward, double x, double y, double halfAngleDegrees){
        Vec3 normal = forward.normalize();
        if(normal.lengthSqr() <= 1.0E-12D) return Vec3.ZERO;
        Vec3 reference = Math.abs(normal.y) > 0.999D ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
        Vec3 right = normal.cross(reference).normalize();
        if(Math.abs(normal.y) > 0.999D) right = right.scale(-1.0D);
        Vec3 up = right.cross(normal).normalize();
        double tangent = Math.tan(Math.toRadians(Math.min(89.0D, ScmThrustGeometry.coneDegrees(halfAngleDegrees))));
        return normal.subtract(right.scale(coordinate(x) * tangent)).subtract(up.scale(coordinate(y) * tangent)).normalize();
    }

    private static double coordinate(double val){ return Double.isFinite(val) ? Math.clamp(val, -1.0D, 1.0D) : 0.0D; }
    private static Vector3d vector(Vec3 val){ return new Vector3d(val.x, val.y, val.z); }
}
