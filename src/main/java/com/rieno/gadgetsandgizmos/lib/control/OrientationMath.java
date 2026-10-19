package com.rieno.gadgetsandgizmos.lib.control;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

// Build directions from local X/Z angles and apply common input limits
public final class OrientationMath {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the orientation math
    private OrientationMath() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the direction from angles
    public static Vec3 directionFromAngles(double xAngleRadians, double zAngleRadians) {
        return new Vec3(Math.tan(zAngleRadians), 1.0D, Math.tan(xAngleRadians)).normalize();
    }

    // Return pitch, yaw and roll in degrees with yaw spanning the full heading range
    public static Vec3 pitchYawRollDegrees(Quaterniondc orientation){
        if(orientation == null || !Double.isFinite(orientation.x())
                || !Double.isFinite(orientation.y()) || !Double.isFinite(orientation.z())
                || !Double.isFinite(orientation.w()) || orientation.lengthSquared() <= 1.0E-24D)
            return Vec3.ZERO;
        Vector3d angles = new Quaterniond(orientation).normalize().getEulerAnglesYXZ(new Vector3d());
        return new Vec3(Math.toDegrees(angles.x), Math.toDegrees(angles.y), Math.toDegrees(angles.z));
    }

    // Clamp the orientation math
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    // Apply the deadzone
    public static double applyDeadzone(double value, double deadzone) {
        return Math.abs(value) < deadzone ? 0.0D : value;
    }
}
