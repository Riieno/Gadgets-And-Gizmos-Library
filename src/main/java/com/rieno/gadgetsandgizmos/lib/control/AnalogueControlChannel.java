package com.rieno.gadgetsandgizmos.lib.control;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// Define the built-in flight channels with their axis direction and default behavior
public enum AnalogueControlChannel {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    PITCH_UP("pitch_up", "pitch", true, "createthrusters.analogue_controller.channel.pitch_up", AnalogueChannelMode.RAMP),
    PITCH_DOWN("pitch_down", "pitch", false, "createthrusters.analogue_controller.channel.pitch_down", AnalogueChannelMode.RAMP),
    ROLL_LEFT("roll_left", "roll", false, "createthrusters.analogue_controller.channel.roll_left", AnalogueChannelMode.RAMP),
    ROLL_RIGHT("roll_right", "roll", true, "createthrusters.analogue_controller.channel.roll_right", AnalogueChannelMode.RAMP),
    YAW_LEFT("yaw_left", "yaw", false, "createthrusters.analogue_controller.channel.yaw_left", AnalogueChannelMode.RAMP),
    YAW_RIGHT("yaw_right", "yaw", true, "createthrusters.analogue_controller.channel.yaw_right", AnalogueChannelMode.RAMP),
    THROTTLE_UP("throttle_up", "throttle", true, "createthrusters.analogue_controller.channel.throttle_up", AnalogueChannelMode.STEP),
    THROTTLE_DOWN("throttle_down", "throttle", false, "createthrusters.analogue_controller.channel.throttle_down", AnalogueChannelMode.STEP),
    STABILIZE("stabilize", "stabilize", true, "createthrusters.analogue_controller.channel.stabilize", AnalogueChannelMode.LATCH),
    STRAFE_LEFT("strafe_left", "strafe", false, "createthrusters.analogue_controller.channel.strafe_left", AnalogueChannelMode.RAMP),
    STRAFE_RIGHT("strafe_right", "strafe", true, "createthrusters.analogue_controller.channel.strafe_right", AnalogueChannelMode.RAMP),
    LIFT_UP("lift_up", "lift", true, "createthrusters.analogue_controller.channel.lift_up", AnalogueChannelMode.RAMP),
    LIFT_DOWN("lift_down", "lift", false, "createthrusters.analogue_controller.channel.lift_down", AnalogueChannelMode.RAMP);

    private static final Map<String, AnalogueControlChannel> BY_ID = Arrays.stream(values())
            .collect(Collectors.toMap(AnalogueControlChannel::id, Function.identity()));

    // Analogue control channel id
    private final String id;
    // Axis id
    private final String axisId;
    // Tracks whether the axis is positive
    private final boolean positiveAxis;
    // Translation key
    private final String translationKey;
    // Default mode
    private final AnalogueChannelMode defaultMode;

    // Initialize the analogue control channel
    AnalogueControlChannel(String id, String axisId, boolean positiveAxis, String translationKey, AnalogueChannelMode defaultMode) {
        this.id = id;
        this.axisId = axisId;
        this.positiveAxis = positiveAxis;
        this.translationKey = translationKey;
        this.defaultMode = defaultMode;
    }

    // Get the id
    public String id() {
        return id;
    }

    // Get the axis id
    public String axisId() {
        return axisId;
    }

    // Check if this is the positive axis
    public boolean isPositiveAxis() {
        return positiveAxis;
    }

    // Get the translation key
    public String translationKey() {
        return translationKey;
    }

    // Create the default mode
    public AnalogueChannelMode defaultMode() {
        return defaultMode;
    }

    // Find the analogue control channel by id
    public static AnalogueControlChannel byId(String id) {
        if (id == null) {
            return null;
        }
        return BY_ID.get(id.trim().toLowerCase(Locale.ROOT));
    }
}
