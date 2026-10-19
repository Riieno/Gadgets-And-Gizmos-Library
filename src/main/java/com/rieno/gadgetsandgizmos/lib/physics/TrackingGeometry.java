package com.rieno.gadgetsandgizmos.lib.physics;

import net.minecraft.world.phys.Vec3;

// Measure targets in a caller supplied world frame
public final class TrackingGeometry {
    private TrackingGeometry(){
    }

    // Measure signed yaw and pitch in degrees from the observer's forward and up axes
    public static Angles relativeAngles(Vec3 origin, Vec3 target, Vec3 forward, Vec3 up){
        Vec3 offset = target.subtract(origin);
        if(offset.lengthSqr() < 1.0E-12D) return new Angles(0.0D, 0.0D);
        Vec3 right = forward.cross(up).normalize();
        double ahead = offset.dot(forward.normalize());
        double side = offset.dot(right);
        double vertical = offset.dot(up.normalize());
        return new Angles(Math.toDegrees(Math.atan2(side, ahead)),
                Math.toDegrees(Math.atan2(vertical, Math.hypot(ahead, side))));
    }

    // Store a signed relative bearing
    public record Angles(double yaw, double pitch){
    }
}
