package com.rieno.gadgetsandgizmos.lib.control;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

// List the controller mechanics which can be bound to an analogue channel
public enum ControllerMechanic {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    PITCH("pitch", "createthrusters.analogue_controller.mode.pitch"),
    ROLL("roll", "createthrusters.analogue_controller.mode.roll"),
    YAW("yaw", "createthrusters.analogue_controller.mode.yaw"),
    THROTTLE("throttle", "createthrusters.analogue_controller.mode.throttle"),
    STRAFE("strafe", "createthrusters.analogue_controller.mode.strafe"),
    LIFT("lift", "createthrusters.analogue_controller.mode.lift"),

    CUSTOM("custom", "createthrusters.analogue_controller.mode.custom");

    // Controller mechanic id
    private final String id;
    // Translation key
    private final String translationKey;

    // Initialize the controller mechanic
    ControllerMechanic(String id, String translationKey) {
        this.id = id;
        this.translationKey = translationKey;
    }

    // Get the id
    public String id() {
        return id;
    }

    // Get the translation key
    public String translationKey() {
        return translationKey;
    }

    // Find the controller mechanic by id
    public static @Nullable ControllerMechanic byId(String id) {
        if (id == null) {
            return null;
        }

        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (ControllerMechanic mechanic : values()) {
            if (mechanic.id.equals(normalized)) {
                return mechanic;
            }
        }
        return null;
    }
}
