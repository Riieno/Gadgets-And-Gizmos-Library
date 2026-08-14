package com.rieno.gadgetsandgizmos.lib.discovery;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

// Define stable discovery kinds with their UI translation keys
public enum ControllerDiscoveryKind {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    THRUSTER("thruster", "createthrusters.analogue_controller.discovery.thruster"),
    FAN("fan", "createthrusters.analogue_controller.discovery.fan"),
    BEARING("bearing", "createthrusters.analogue_controller.discovery.bearing"),
    VECTOR_BEARING("vector_bearing", "createthrusters.analogue_controller.discovery.vector_bearing"),
    REDSTONE_LINK("redstone_link", "createthrusters.analogue_controller.discovery.redstone_link"),
    GYROSCOPE_LINK("gyroscope_link", "createthrusters.analogue_controller.discovery.gyroscope_link"),
    JOYSTICK("joystick", "createthrusters.analogue_controller.discovery.joystick"),
    DOUBLE_BUTTON("double_button", "createthrusters.analogue_controller.discovery.double_button"),
    ANALOG_LEVER("analog_lever", "createthrusters.analogue_controller.discovery.analog_lever"),
    THROTTLE("throttle", "createthrusters.analogue_controller.discovery.throttle"),
    ANALOG_TRANSMISSION("analog_transmission", "createthrusters.analogue_controller.discovery.analog_transmission"),
    GIMBAL_SENSOR("gimbal_sensor", "createthrusters.analogue_controller.discovery.gimbal_sensor"),
    MAGNET("magnet", "createthrusters.analogue_controller.discovery.magnet"),
    CLAW("claw", "createthrusters.analogue_controller.discovery.claw"),
    STEERING_WHEEL("steering_wheel", "createthrusters.analogue_controller.discovery.steering_wheel"),
    NAVIGATION_TABLE("navigation_table", "createthrusters.analogue_controller.discovery.navigation_table"),
    WHEEL_MOUNT("wheel_mount", "createthrusters.analogue_controller.discovery.wheel_mount"),
    KINETIC("kinetic", "createthrusters.analogue_controller.discovery.kinetic"),
    MACHINE("machine", "createthrusters.analogue_controller.discovery.machine"),
    DISPLAY("display", "createthrusters.analogue_controller.discovery.display"),
    DISPLAY_ADAPTER("display_adapter", "createthrusters.analogue_controller.discovery.display_adapter"),
    LINKER_FACE_INPUT("linker_face_input", "createthrusters.analogue_controller.discovery.linker_face_input"),
    LINKER_FACE_OUTPUT("linker_face_output", "createthrusters.analogue_controller.discovery.linker_face_output"),
    UNKNOWN("unknown", "createthrusters.analogue_controller.discovery.unknown");

    // Controller discovery kind id
    private final String id;
    // Translation key
    private final String translationKey;

    // Initialize the controller discovery kind
    ControllerDiscoveryKind(String id, String translationKey) {
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

    // Get the summary translation key
    public String summaryTranslationKey() {
        return translationKey + ".summary";
    }

    // Find the controller discovery kind by id
    public static @Nullable ControllerDiscoveryKind byId(String id) {
        if (id == null) {
            return null;
        }

        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (ControllerDiscoveryKind kind : values()) {
            if (kind.id.equals(normalized)) {
                return kind;
            }
        }
        return null;
    }
}
