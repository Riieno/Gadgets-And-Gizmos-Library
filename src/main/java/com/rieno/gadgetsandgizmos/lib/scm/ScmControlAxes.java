package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Convert physical control vectors to authored directional controls. */
public final class ScmControlAxes {
    private ScmControlAxes(){}

    /** Positive rotation about up turns forward towards left, not right. */
    public static double yawRightDemand(Vec3 torque, Vec3 up, boolean reverseSteering){
        double right = -projection(torque, up);
        return reverseSteering ? -right : right;
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

    private static double projection(Vec3 vector, Vec3 axis){
        if(!finite(vector) || !finite(axis)) return 0.0D;
        double result = vector.dot(axis.normalize());
        return Double.isFinite(result) ? result : 0.0D;
    }

    private static boolean finite(Vec3 value){
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
