package com.rieno.gadgetsandgizmos.lib.control.hardware;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Define stable controller bindings and the default layout used for flight controls
public final class HardwareControllerBindings {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final String PREFIX = "hardware:";
    public static final double DEFAULT_DEADZONE = 0.12D;
    public static final int MAX_RAW_AXES = 16;
    public static final int MAX_RAW_BUTTONS = 32;

    private static final List<String> STANDARD_AXIS_IDS = List.of(
            "left_x", "left_y", "right_x", "right_y", "left_trigger", "right_trigger");
    private static final List<String> STANDARD_BUTTON_IDS = List.of(
            "button_a", "button_b", "button_x", "button_y",
            "left_bumper", "right_bumper", "back", "start", "guide",
            "left_stick", "right_stick", "dpad_up", "dpad_right", "dpad_down", "dpad_left");
    private static final List<String> STANDARD_BUTTON_LABELS = List.of(
            "A Button", "B Button", "X Button", "Y Button",
            "Left Bumper", "Right Bumper", "Back", "Start", "Guide",
            "Left Stick Button", "Right Stick Button", "D-pad Up", "D-pad Right", "D-pad Down", "D-pad Left");

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the hardware controller bindings
    private HardwareControllerBindings() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this is a hardware binding
    public static boolean isHardwareBinding(String id) {
        return id != null && id.startsWith(PREFIX);
    }

    // Get the options
    public static List<BindingOption> options() {
        List<BindingOption> opts = new ArrayList<>();
        opts.add(new BindingOption(PREFIX + "left_x", "Gamepad Left Stick X"));
        opts.add(new BindingOption(PREFIX + "left_y", "Gamepad Left Stick Y"));
        opts.add(new BindingOption(PREFIX + "right_x", "Gamepad Right Stick X"));
        opts.add(new BindingOption(PREFIX + "right_y", "Gamepad Right Stick Y"));
        opts.add(new BindingOption(PREFIX + "left_trigger", "Gamepad Left Trigger"));
        opts.add(new BindingOption(PREFIX + "right_trigger", "Gamepad Right Trigger"));
        for (int idx = 0; idx < STANDARD_BUTTON_IDS.size(); idx++) {
            opts.add(new BindingOption(PREFIX + STANDARD_BUTTON_IDS.get(idx),
                    "Gamepad " + STANDARD_BUTTON_LABELS.get(idx)));
        }
        for (int idx = 0; idx < MAX_RAW_AXES; idx++) {
            opts.add(new BindingOption(PREFIX + "axis_" + idx, "Controller Axis " + idx));
        }
        for (int idx = 0; idx < MAX_RAW_BUTTONS; idx++) {
            opts.add(new BindingOption(PREFIX + "button_" + idx, "Controller Button " + idx));
        }
        return List.copyOf(opts);
    }

    // Get the values
    public static Map<String, Double> values(HardwareControllerState state) {
        return values(state, DEFAULT_DEADZONE);
    }

    // Get the values
    public static Map<String, Double> values(HardwareControllerState state, double deadzone) {
        if (state == null) {
            return Map.of();
        }
        double threshold = clamp(deadzone, 0.0D, 0.95D);
        Map<String, Double> values = new LinkedHashMap<>();
        int axisCount = Math.min(state.axes().length, MAX_RAW_AXES);
        for (int idx = 0; idx < axisCount; idx++) {
            values.put(PREFIX + "axis_" + idx, applyDeadzone(state.axis(idx), threshold));
        }
        if (state.standardized()) {
            for (int idx = 0; idx < Math.min(STANDARD_AXIS_IDS.size(), axisCount); idx++) {
                double val = state.axis(idx);
                if (idx >= 4) {
                    val = (val + 1.0D) * 0.5D;
                } else {
                    val = applyDeadzone(val, threshold);
                }
                values.put(PREFIX + STANDARD_AXIS_IDS.get(idx), clamp(val, idx >= 4 ? 0.0D : -1.0D, 1.0D));
            }
        }
        int buttonCount = Math.min(state.buttons().length, MAX_RAW_BUTTONS);
        for (int idx = 0; idx < buttonCount; idx++) {
            double val = state.button(idx) ? 1.0D : 0.0D;
            values.put(PREFIX + "button_" + idx, val);
            if (state.standardized() && idx < STANDARD_BUTTON_IDS.size()) {
                values.put(PREFIX + STANDARD_BUTTON_IDS.get(idx), val);
            }
        }
        return Collections.unmodifiableMap(values);
    }

    // Get the conventional channels
    public static Map<String, Double> conventionalChannels(Map<String, Double> hardwareValues) {
        if (hardwareValues == null || hardwareValues.isEmpty()) {
            return zeroedConventionalChannels();
        }
        double rightX = value(hardwareValues, "right_x", "axis_2");
        double rightY = value(hardwareValues, "right_y", "axis_3");
        double leftX = value(hardwareValues, "left_x", "axis_0");
        double leftY = value(hardwareValues, "left_y", "axis_1");
        Map<String, Double> channels = new LinkedHashMap<>();
        channels.put("pitch_up", positive(-rightY));
        channels.put("pitch_down", positive(rightY));
        channels.put("roll_left", positive(-rightX));
        channels.put("roll_right", positive(rightX));
        channels.put("yaw_left", positive(-leftX));
        channels.put("yaw_right", positive(leftX));
        channels.put("throttle_up", positive(value(hardwareValues, "right_trigger", "axis_5")));
        channels.put("throttle_down", positive(value(hardwareValues, "left_trigger", "axis_4")));
        channels.put("stabilize", positive(value(hardwareValues, "button_a", "button_0")));
        channels.put("strafe_left", Math.max(
                positive(value(hardwareValues, "left_bumper", "button_4")),
                positive(value(hardwareValues, "dpad_left", "button_14"))));
        channels.put("strafe_right", Math.max(
                positive(value(hardwareValues, "right_bumper", "button_5")),
                positive(value(hardwareValues, "dpad_right", "button_12"))));
        channels.put("lift_up", Math.max(positive(-leftY),
                positive(value(hardwareValues, "dpad_up", "button_11"))));
        channels.put("lift_down", Math.max(positive(leftY),
                positive(value(hardwareValues, "dpad_down", "button_13"))));
        return Collections.unmodifiableMap(channels);
    }

    // Apply the deadzone
    public static double applyDeadzone(double value, double deadzone) {
        double finite = Double.isFinite(value) ? clamp(value, -1.0D, 1.0D) : 0.0D;
        double threshold = clamp(deadzone, 0.0D, 0.95D);
        double magnitude = Math.abs(finite);
        if (magnitude <= threshold) {
            return 0.0D;
        }
        return Math.copySign((magnitude - threshold) / (1.0D - threshold), finite);
    }

    // Get the zeroed conventional channels
    private static Map<String, Double> zeroedConventionalChannels() {
        Map<String, Double> channels = new LinkedHashMap<>();
        for (String id : List.of("pitch_up", "pitch_down", "roll_left", "roll_right",
                "yaw_left", "yaw_right", "throttle_up", "throttle_down", "stabilize",
                "strafe_left", "strafe_right", "lift_up", "lift_down")) {
            channels.put(id, 0.0D);
        }
        return Collections.unmodifiableMap(channels);
    }

    // Get the value
    private static double value(Map<String, Double> values, String standardId, String rawId) {
        Double standard = values.get(PREFIX + standardId);
        Double raw = values.get(PREFIX + rawId);
        double res = standard != null ? standard : raw == null ? 0.0D : raw;
        return Double.isFinite(res) ? clamp(res, -1.0D, 1.0D) : 0.0D;
    }

    // Get the positive
    private static double positive(double val) {
        return clamp(val, 0.0D, 1.0D);
    }

    // Clamp the hardware controller bindings
    private static double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    // Store the binding option
    public record BindingOption(String id, String label) {
        // Initialize the binding option
        public BindingOption {
            id = id == null ? "" : id;
            label = label == null ? id : label;
        }
    }
}
