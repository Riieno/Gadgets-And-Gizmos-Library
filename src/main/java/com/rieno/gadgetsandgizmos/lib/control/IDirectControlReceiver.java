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

    // Select one side of an exclusive two-channel control. Implementations may
    // override this to update both sides atomically; the default always clears
    // the inactive channel before asserting the selected channel.
    default void applyExclusiveDirectControllerSignal(
            String activeChannelId,
            String inactiveChannelId,
            float val
    ) {
        applyDirectControllerSignal(inactiveChannelId, 0.0F);
        applyDirectControllerSignal(activeChannelId, val);
    }
}
