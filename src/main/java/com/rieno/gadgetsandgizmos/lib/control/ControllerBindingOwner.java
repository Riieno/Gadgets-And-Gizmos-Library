package com.rieno.gadgetsandgizmos.lib.control;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.Locale;

// Identify which system owns a controller binding
public enum ControllerBindingOwner {
    USER("user"),
    GRAPH("graph");

    private final String id;

    // Initialize the controller binding owner
    ControllerBindingOwner(String id) {
        this.id = id;
    }

    // Get the serialized owner id
    public String id() {
        return id;
    }

    // Resolve a serialized owner id
    public static ControllerBindingOwner fromId(String id) {
        if (id == null || id.isBlank()) {
            return USER;
        }
        String normalized = id.strip().toLowerCase(Locale.ROOT);
        for (ControllerBindingOwner owner : values()) {
            if (owner.id.equals(normalized)) {
                return owner;
            }
        }
        return USER;
    }
}
