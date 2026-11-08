package com.rieno.gadgetsandgizmos.lib.navigation;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

// Publish one active destination and an estimate based on remaining travel and live motion
public record NavigationProgress(@Nullable Vec3 target, double remainingDistance, long etaSeconds){
    public static final NavigationProgress INACTIVE = new NavigationProgress(null, 0, -1);

    public boolean active(){ return target != null; }

    // Include acceleration and final braking without imposing a cruise limit on the actual controller
    public static long estimateSeconds(double distance, double tolerance, double actualSpeed,
                                       double requestedSpeed, double acceleration, double braking){
        if(!Double.isFinite(distance) || !Double.isFinite(tolerance) || distance < 0) return -1;
        double remaining = Math.max(0, distance - Math.max(0, tolerance));
        if(remaining <= 1.0E-6) return 0;
        if(!Double.isFinite(actualSpeed) || !Double.isFinite(requestedSpeed)) return -1;
        double speed = Math.max(0, actualSpeed), limit = Math.max(0, requestedSpeed);
        if(limit <= 1.0E-6) return -1;
        if(!Double.isFinite(acceleration) || !Double.isFinite(braking) || acceleration <= 1.0E-6 || braking <= 1.0E-6){
            return seconds(remaining / (speed > 1.0E-6 ? speed : limit));
        }
        double stopDistance = speed * speed / (2 * braking);
        if(remaining <= stopDistance){
            return seconds(2 * remaining / (speed + Math.sqrt(Math.max(0, speed * speed - 2 * braking * remaining))));
        }
        double peak = Math.sqrt((2 * acceleration * braking * remaining + braking * speed * speed) / (acceleration + braking));
        peak = Math.min(peak, Math.max(speed, limit));
        double gainDistance = Math.max(0, peak * peak - speed * speed) / (2 * acceleration);
        double brakeDistance = peak * peak / (2 * braking);
        double cruiseDistance = Math.max(0, remaining - gainDistance - brakeDistance);
        return seconds(Math.max(0, peak - speed) / acceleration + cruiseDistance / peak + peak / braking);
    }

    private static long seconds(double val){ return Double.isFinite(val) ? Math.max(1, (long)Math.ceil(val)) : -1; }
}
