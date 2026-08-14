package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

// Describe one two-way Sable link starting from the body which owns its actor
public record SableAssemblyConnection(@Nullable UUID targetSubLevelId, Kind kind) {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the sable assembly connection
    public SableAssemblyConnection {
        kind = kind == null ? Kind.STRUCTURAL : kind;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the structural
    public static SableAssemblyConnection structural(@Nullable UUID targetSubLevelId) {
        return new SableAssemblyConnection(targetSubLevelId, Kind.STRUCTURAL);
    }

    // Get the carriage coupler
    public static SableAssemblyConnection carriageCoupler(@Nullable UUID targetSubLevelId) {
        return new SableAssemblyConnection(targetSubLevelId, Kind.CARRIAGE_COUPLER);
    }

    // Define the kind values
    public enum Kind {
        STRUCTURAL,
        CARRIAGE_COUPLER
    }
}
