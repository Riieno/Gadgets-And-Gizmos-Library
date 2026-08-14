package com.rieno.gadgetsandgizmos.lib.scm;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

// Define how scheduled SCM movement turns a navigation target into ship-local flight demand
public enum ScmFlightBehavior {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    DIRECT_VECTOR("direct_vector"),
    PREFER_SHIP_DIRECTION("prefer_ship_direction");

    public static final ScmFlightBehavior DEFAULT = DIRECT_VECTOR;
    private static final List<String> IDS = Arrays.stream(values())
            .map(ScmFlightBehavior::id)
            .toList();

    // SCM flight behavior id
    private final String id;

    // Initialize the SCM flight behavior
    ScmFlightBehavior(String id) {
        this.id = id;
    }

    // Get the id
    public String id() {
        return id;
    }

    // Get the ids
    public static List<String> ids() {
        return IDS;
    }

    // Check if the ID is known
    public static boolean isKnownId(@Nullable String id) {
        return find(id) != null;
    }

    // Create the SCM flight behavior from id
    public static ScmFlightBehavior fromId(@Nullable String id) {
        ScmFlightBehavior behavior = find(id);
        return behavior == null ? DEFAULT : behavior;
    }

    // Find the SCM flight behavior
    private static @Nullable ScmFlightBehavior find(@Nullable String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (ScmFlightBehavior behavior : values()) {
            if (behavior.id.equals(normalized)) {
                return behavior;
            }
        }
        return null;
    }
}
