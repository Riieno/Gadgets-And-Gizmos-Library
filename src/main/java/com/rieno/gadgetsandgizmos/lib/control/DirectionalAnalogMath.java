package com.rieno.gadgetsandgizmos.lib.control;
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.gimbal.CardinalTiltController;
import net.minecraft.util.Mth;

// Convert local input axes into directional forward, backward and side values
public final class DirectionalAnalogMath {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the directional analog math
    private DirectionalAnalogMath() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the directional analog math from local
    public static DirectionalAnalogSnapshot fromLocal(double localX, double localZ, double deadzone) {
        double clampedX = Mth.clamp(localX, -1.0D, 1.0D);
        double clampedZ = Mth.clamp(localZ, -1.0D, 1.0D);
        double magnitude = Math.sqrt(clampedX * clampedX + clampedZ * clampedZ);
        return fromClamped(clampedX, clampedZ, deadzone, magnitude);
    }

    // Create the directional analog math from square local
    public static DirectionalAnalogSnapshot fromSquareLocal(double localX, double localZ, double deadzone) {
        double clampedX = Mth.clamp(localX, -1.0D, 1.0D);
        double clampedZ = Mth.clamp(localZ, -1.0D, 1.0D);
        double magnitude = Math.max(Math.abs(clampedX), Math.abs(clampedZ));
        return fromClamped(clampedX, clampedZ, deadzone, magnitude);
    }

    // Create the directional analog math from clamped
    private static DirectionalAnalogSnapshot fromClamped(double clampedX, double clampedZ, double deadzone,
                                                          double magnitude) {
        if (magnitude <= Math.max(0.0D, deadzone)) {
            return DirectionalAnalogSnapshot.ZERO;
        }

        double normalizedMagnitude = Mth.clamp((magnitude - deadzone) / Math.max(1.0E-6D, 1.0D - deadzone), 0.0D, 1.0D);
        double scale = normalizedMagnitude / Math.max(magnitude, 1.0E-6D);
        double scaledX = clampedX * scale;
        double scaledZ = clampedZ * scale;
        return new DirectionalAnalogSnapshot(
                scaledX,
                scaledZ,

            Math.max(0.0D, scaledZ),
            Math.max(0.0D, -scaledZ),
            Math.max(0.0D, scaledX),
            Math.max(0.0D, -scaledX),
                normalizedMagnitude);
    }

    // Convert the directional analog math to cardinal pulls
    public static CardinalTiltController.CardinalPulls toCardinalPulls(DirectionalAnalogSnapshot snapshot) {
        if (snapshot == null) {
            return CardinalTiltController.CardinalPulls.ZERO;
        }
        return new CardinalTiltController.CardinalPulls(
                snapshot.forward(),
                snapshot.backward(),
                snapshot.right(),
                snapshot.left());
    }
}
