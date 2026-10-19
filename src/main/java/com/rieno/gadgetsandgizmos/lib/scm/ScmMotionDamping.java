package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.world.phys.Vec3;

// Request counter acceleration without changing a body's position or velocity directly
public final class ScmMotionDamping{
    private static final double RESPONSE_SECONDS = 0.5D;

    private ScmMotionDamping(){}

    // Stop translation and rotation in the caller's common coordinate frame
    public static Demand stop(Vec3 velocity, Vec3 angularVelocity, double strength){
        double gain = gain(strength);
        return new Demand(finite(velocity).scale(-gain), finite(angularVelocity).scale(-gain));
    }

    // Stop translation while leaving angular control with the caller
    public static Vec3 stopLinear(Vec3 velocity, double strength){
        return finite(velocity).scale(-gain(strength));
    }

    // Stop only rotation about the supplied yaw axis
    public static Vec3 holdYaw(Vec3 angularVelocity, Vec3 up, double strength){
        Vec3 axis = finite(up);
        if(axis.lengthSqr() <= 1.0E-12D) return Vec3.ZERO;
        axis = axis.normalize();
        return axis.scale(-finite(angularVelocity).dot(axis) * gain(strength));
    }

    // Stop translation at a strength-selected recovery speed without reversing the measured velocity
    public static Vec3 stopLinear(Vec3 velocity, double strength, double tickSeconds){
        double gain = ScmStateFeedback.recoveryVelocityAcceleration(0.0D, 1.0D, strength, tickSeconds);
        return gain == 0.0D ? Vec3.ZERO : finite(velocity).scale(gain);
    }

    // Counter only yaw with full-strength recovery requested within one control interval
    public static Vec3 holdYaw(Vec3 angularVelocity, Vec3 up, double strength, double tickSeconds){
        Vec3 axis = finite(up);
        if(axis.lengthSqr() <= 1.0E-12D) return Vec3.ZERO;
        axis = axis.normalize();
        double acceleration = ScmStateFeedback.recoveryVelocityAcceleration(
                0.0D, finite(angularVelocity).dot(axis), strength, tickSeconds);
        return acceleration == 0.0D ? Vec3.ZERO : axis.scale(acceleration);
    }

    // Measure signed angular speed about the supplied axis in radians per second
    public static double yawRate(Vec3 angularVelocity, Vec3 up){
        Vec3 axis = finite(up);
        if(axis.lengthSqr() <= 1.0E-12D) return 0.0D;
        return finite(angularVelocity).dot(axis.normalize());
    }

    // Hold a captured forward direction while leaving pitch and roll free
    public static Vec3 holdYaw(Vec3 angularVelocity, Vec3 up, Vec3 currentForward,
                               Vec3 targetForward, double strength){
        Vec3 axis = finite(up);
        if(axis.lengthSqr() <= 1.0E-12D) return Vec3.ZERO;
        axis = axis.normalize();
        double error = yawError(currentForward, targetForward, axis);
        double rate = finite(angularVelocity).dot(axis);
        return axis.scale(ScmControlInputs.strength(strength)
                * (error / (RESPONSE_SECONDS * RESPONSE_SECONDS) - 2.0D * rate / RESPONSE_SECONDS));
    }

    // Measure the shortest signed heading change about the supplied yaw axis
    public static double yawError(Vec3 currentForward, Vec3 targetForward, Vec3 up){
        Vec3 axis = finite(up);
        if(axis.lengthSqr() <= 1.0E-12D) return 0.0D;
        axis = axis.normalize();
        Vec3 current = finite(currentForward);
        Vec3 target = finite(targetForward);
        current = current.subtract(axis.scale(current.dot(axis)));
        target = target.subtract(axis.scale(target.dot(axis)));
        if(current.lengthSqr() <= 1.0E-12D || target.lengthSqr() <= 1.0E-12D) return 0.0D;
        current = current.normalize();
        target = target.normalize();
        return Math.atan2(axis.dot(current.cross(target)), Math.clamp(current.dot(target), -1.0D, 1.0D));
    }

    private static double gain(double strength){
        return ScmControlInputs.strength(strength) / RESPONSE_SECONDS;
    }

    private static Vec3 finite(Vec3 val){
        return val == null ? Vec3.ZERO : new Vec3(
                Double.isFinite(val.x) ? val.x : 0.0D,
                Double.isFinite(val.y) ? val.y : 0.0D,
                Double.isFinite(val.z) ? val.z : 0.0D);
    }

    public record Demand(Vec3 acceleration, Vec3 angularAcceleration){}
}
