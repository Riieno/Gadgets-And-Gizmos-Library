package com.rieno.gadgetsandgizmos.lib.control;

// Accept a normalized analogue value from a transmission controller
public interface AnalogueTransmissionTarget {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the channel id
    String channelId();

    // Accept the analogue transmission target
    void accept(AnalogueSignalPacket packet);

    // Reset the analogue transmission target
    default void reset(long gameTick, String sourceId, String sourceType) {
        accept(new AnalogueSignalPacket(channelId(), 0.0F, gameTick, sourceId, sourceType));
    }
}
