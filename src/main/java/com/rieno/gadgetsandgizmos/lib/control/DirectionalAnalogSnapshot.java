package com.rieno.gadgetsandgizmos.lib.control;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.util.Mth;

// Store one resolved directional analogue input sample
public record DirectionalAnalogSnapshot(double localX, double localZ, double forward, double backward,
                                        double left, double right, double magnitude) {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final DirectionalAnalogSnapshot ZERO = new DirectionalAnalogSnapshot(
            0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Forward the redstone
    public int forwardRedstone() {
        return toRedstone(forward);
    }

    // Get the backward redstone
    public int backwardRedstone() {
        return toRedstone(backward);
    }

    // Get the left redstone
    public int leftRedstone() {
        return toRedstone(left);
    }

    // Get the right redstone
    public int rightRedstone() {
        return toRedstone(right);
    }

    // Convert the directional analog snapshot to redstone
    public static int toRedstone(double strength) {
        return Mth.clamp((int) Math.round(Mth.clamp(strength, 0.0D, 1.0D) * 15.0D), 0, 15);
    }
}
