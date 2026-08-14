package com.rieno.gadgetsandgizmos.lib.kinetics;

// Apply or clear one exact angle on a kinetic output
public interface PreciseKineticOutputAccess {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Apply the precise angle output
    void ct$applyPreciseAngleOutput(float angleDegrees);

    // Clear the precise angle output
    void ct$clearPreciseAngleOutput();
}
