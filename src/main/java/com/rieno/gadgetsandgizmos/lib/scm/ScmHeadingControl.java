package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.world.phys.Vec3;

// Keep a chosen yaw turn stable near a directly opposite heading
public final class ScmHeadingControl{
    private ScmHeadingControl(){}

    public static double yawError(Vec3 forward, Vec3 heading, double yawRate){
        Vec3 first = new Vec3(forward.x, 0, forward.z).normalize();
        Vec3 second = new Vec3(heading.x, 0, heading.z).normalize();
        if(first.lengthSqr() < 1.0E-12D || second.lengthSqr() < 1.0E-12D) return 0;
        double dot = Math.max(-1, Math.min(1, first.dot(second)));
        double cross = first.cross(second).y;
        if(dot < -0.95D){
            double sign = Math.abs(yawRate) > 0.02D ? yawRate : Math.abs(cross) > 0.05D ? cross : 1.0D;
            return Math.copySign(Math.acos(dot), sign);
        }
        return Math.atan2(cross, dot);
    }
}
