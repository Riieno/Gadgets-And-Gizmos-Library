package com.rieno.gadgetsandgizmos.lib.control;

// Accept named direct control values from controllers and SCM probes
public interface IDirectControlReceiver {

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Apply the direct controller signal
    void applyDirectControllerSignal(String channelId, float val);
}
