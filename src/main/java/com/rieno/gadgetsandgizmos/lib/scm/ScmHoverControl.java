package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.world.phys.Vec3;

// Arrest vertical motion without capturing a height or braking horizontal travel
public final class ScmHoverControl{
    private ScmHoverControl(){}

    public static Vec3 descentRecovery(Vec3 velocity, Vec3 supportDirection, double strength, double tickSeconds){
        if(velocity == null || supportDirection == null || supportDirection.lengthSqr() <= 1.0E-12D) return Vec3.ZERO;
        Vec3 up = supportDirection.normalize();
        double falling = velocity.dot(up);
        if(!Double.isFinite(falling) || falling >= 0.0D) return Vec3.ZERO;
        return ScmMotionDamping.stopLinear(up.scale(falling), strength, tickSeconds);
    }

    // Counter rising and falling motion after direct vertical control is released
    public static Vec3 verticalRecovery(Vec3 velocity, Vec3 supportDirection, double strength, double tickSeconds){
        if(velocity == null || supportDirection == null || supportDirection.lengthSqr() <= 1.0E-12D) return Vec3.ZERO;
        Vec3 up = supportDirection.normalize();
        double speed = velocity.dot(up);
        if(!Double.isFinite(speed)) return Vec3.ZERO;
        return ScmMotionDamping.stopLinear(up.scale(speed), strength, tickSeconds);
    }
}
