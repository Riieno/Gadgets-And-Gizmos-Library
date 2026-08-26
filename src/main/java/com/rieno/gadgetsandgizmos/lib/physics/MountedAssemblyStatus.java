package com.rieno.gadgetsandgizmos.lib.physics;

// Define whether a mounted assembly anchor is available and how its owner should recover
public enum MountedAssemblyStatus {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    PRESENT,
    UNAVAILABLE,
    BROKEN,
    INVALID;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this should retry after the anchor becomes available
    public boolean shouldRetry() {
        return this == UNAVAILABLE;
    }

    // Check if this should disassemble the remaining mounted body
    public boolean shouldDisassemble() {
        return this == BROKEN;
    }

    // Check if this should clear invalid ownership data
    public boolean shouldClear() {
        return this == INVALID;
    }
}
