package com.rieno.gadgetsandgizmos.lib.control;

// Supply directional analogue values to reusable controller code
public interface DirectionalAnalogSource {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the directional analog snapshot
    DirectionalAnalogSnapshot getDirectionalAnalogSnapshot();

    // Check if the directional analog is active
    boolean isDirectionalAnalogActive();
}
