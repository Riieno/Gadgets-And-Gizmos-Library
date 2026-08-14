package com.rieno.gadgetsandgizmos.lib.control;

// Accept a linked orientation payload from a controller or sensor
public interface OrientationTarget {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this can accept orientation payload
    default boolean canAcceptOrientationPayload(OrientationPayload payload) {
        return true;
    }

    // Apply the orientation payload
    void applyOrientationPayload(OrientationPayload payload);
}
