package com.rieno.gadgetsandgizmos.lib.kinetics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

// Identify one independently controlled head on a multi-head bearing
public enum BearingHead {
    PRIMARY("primary", 0x00B7C8),
    SECONDARY("secondary", 0xF28C28);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Serialized head name
    private final String serializedName;

    // Display colour
    private final int color;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the bearing head
    BearingHead(String serializedName, int col) {
        this.serializedName = serializedName;
        color = col;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the serialized head name
    public String serializedName() {
        return serializedName;
    }

    // Get the display colour
    public int color() {
        return color;
    }

    // Get a bearing head by name
    public static @Nullable BearingHead byName(String name, @Nullable BearingHead fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("cyan") || normalized.equals("left") || normalized.equals("top")) {
            return PRIMARY;
        }
        if (normalized.equals("orange") || normalized.equals("right") || normalized.equals("bottom")) {
            return SECONDARY;
        }
        for (BearingHead head : values()) {
            if (head.serializedName.equals(normalized) || head.name().equalsIgnoreCase(normalized)) {
                return head;
            }
        }
        return fallback;
    }
}
