package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Convert physical control vectors to authored directional controls. */
public final class ScmControlAxes {
    private static final double MAX_GROUND_STEERING_ANGLE = Math.toRadians(30.0D);
    private static final double GROUND_STEERING_YAW_RATE_SECONDS = 0.35D;

    private ScmControlAxes(){}

    /** Positive rotation about up turns forward towards left, not right. */
    public static double yawRightDemand(Vec3 torque, Vec3 up, boolean reverseSteering){
        double right = -projection(torque, up);
        return reverseSteering ? -right : right;
    }

    /**
     * Convert physical yaw to Offroad's signed WheelMount input. Negative selects its
     * left signal and positive selects its right signal; reverse travel inverts steering once.
     */
    public static double wheelSteeringDemand(Vec3 torque, Vec3 up, boolean reverseSteering){
        return -yawRightDemand(torque, up, reverseSteering);
    }

    /**
     * Convert the current heading error directly into a ground steering input.
     * The two-argument form preserves the scalar API for callers which do not
     * have measured yaw-rate feedback.
     */
    public static double groundSteeringDemand(
            double headingError,
            double steeringFeedForward
    ){
        return groundSteeringDemand(headingError, 0.0D, steeringFeedForward);
    }

    /** Combine heading, yaw-rate feedback and route curvature into one analogue steering demand. */
    public static double groundSteeringDemand(
            double headingError,
            double yawRate,
            double steeringFeedForward
    ){
        double error = Double.isFinite(headingError) ? headingError : 0.0D;
        double rate = Double.isFinite(yawRate) ? yawRate : 0.0D;
        double feedForward = Double.isFinite(steeringFeedForward)
                ? steeringFeedForward : 0.0D;
        return Mth.clamp(error / MAX_GROUND_STEERING_ANGLE + feedForward
                        - rate * GROUND_STEERING_YAW_RATE_SECONDS,
                -1.0D, 1.0D);
    }

    /** Construct physical torque for a signed right-turn request (negative means left). */
    public static Vec3 yawTorque(Vec3 up, double rightDemand){
        if(!finite(up) || !Double.isFinite(rightDemand)) return Vec3.ZERO;
        return up.normalize().scale(-rightDemand);
    }

    /**
     * Project force onto forward. A selected travel gear permits propulsion only in that
     * direction; opposing force is braking, not a request to energize the selected gear.
     * Zero travel direction leaves reversible force actuators free to brake in either direction.
     */
    public static double longitudinalDrive(Vec3 force, Vec3 forward, double travelDirection){
        double demand = projection(force, forward);
        if(Double.isFinite(travelDirection) && Math.abs(travelDirection) > 1.0E-5D
                && demand * travelDirection <= 0.0D) return 0.0D;
        return Mth.clamp(demand, -1.0D, 1.0D);
    }

    // Preserve lift support for hovering and climbing without reversing an authored descent
    public static double liftSelector(Vec3 correction, Vec3 physicalForce, Vec3 up, boolean regulatedSpeed){
        if(!regulatedSpeed) return Mth.clamp(projection(correction, up), -1.0D, 1.0D);
        double lift = projection(physicalForce, up);
        return Math.abs(lift) <= 1.0E-5D ? 0.0D : Math.signum(lift);
    }

    // Preserve lift support for hovering and climbing without reversing an authored descent
    public static Vec3 withLiftSupport(Vec3 force, Vec3 gravityHold, Vec3 up){
        if(!finite(force)) force = Vec3.ZERO;
        if(!finite(up) || up.lengthSqr() <= 1.0E-12D) return force;
        Vec3 axis = up.normalize();
        double lift = projection(force, axis);
        if(lift < -1.0E-5D) return force;
        double support = Math.max(0.0D, projection(gravityHold, axis));
        return force.add(axis.scale(Math.max(lift, support) - lift));
    }

    private static double projection(Vec3 vector, Vec3 axis){
        if(!finite(vector) || !finite(axis)) return 0.0D;
        double result = vector.dot(axis.normalize());
        return Double.isFinite(result) ? result : 0.0D;
    }

    private static boolean finite(Vec3 value){
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
