package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

// Select navigation speed from the ship's live stopping authority instead of a fixed cruise ceiling
public final class ScmNavigationSpeed{
    private ScmNavigationSpeed(){}

    // Use the acceleration available without sacrificing the model's other controlled axes
    public static double brakingAcceleration(@Nullable ScmAdaptiveStateModel model, Vec3 direction, double fallback){
        if(model != null && model.available() && direction.lengthSqr() > 1.0E-12D){
            double braking = model.authority(direction.normalize(), false, false);
            if(Double.isFinite(braking) && braking > 1.0E-8D) return braking;
        }
        return Double.isFinite(fallback) ? Math.max(1.0E-8D, fallback) : 1.0E-8D;
    }

    // A percentage selects the fastest speed which can still stop at the destination
    public static double requestedSpeed(double fraction, double distance, double tolerance,
                                         double responseSeconds, double brakingAcceleration){
        double requested = Double.isFinite(fraction) ? Math.clamp(fraction, 0.0D, 1.0D) : 0.0D;
        return requested * ScmSpeedControl.stoppingSpeed(distance, tolerance, responseSeconds, brakingAcceleration);
    }

    // Scale percentages against the configured maximum while preserving the destination stopping envelope
    public static double requestedSpeed(double fraction, double distance, double tolerance,
                                         double responseSeconds, double brakingAcceleration, ScmSpeedLimit limit){
        double safe = ScmSpeedControl.stoppingSpeed(distance, tolerance, responseSeconds, brakingAcceleration);
        return Math.min(safe, limit.percentage(fraction, safe));
    }

    // Include the acceleration possible before the next control update in the collision scan
    public static double scanSpeed(@Nullable ScmAdaptiveStateModel model, Vec3 direction,
                                    double actualSpeed, double tickSeconds){
        double current = Double.isFinite(actualSpeed) ? Math.max(0.0D, actualSpeed) : 0.0D;
        if(model == null || !model.available() || direction.lengthSqr() <= 1.0E-12D) return current;
        double acceleration = model.authority(direction.normalize(), false, true);
        double dt = Double.isFinite(tickSeconds) ? Math.max(0.0D, tickSeconds) : 0.0D;
        return current + (Double.isFinite(acceleration) ? Math.max(0.0D, acceleration) : 0.0D) * dt;
    }
}
