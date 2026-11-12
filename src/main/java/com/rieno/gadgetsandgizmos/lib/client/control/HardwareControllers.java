package com.rieno.gadgetsandgizmos.lib.client.control;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.control.hardware.HardwareControllerBindings;
import com.rieno.gadgetsandgizmos.lib.control.hardware.HardwareControllerState;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;
import org.lwjgl.system.MemoryStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Discover and sample connected controllers on the client thread
public final class HardwareControllers{
    private HardwareControllers(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Keep identical devices distinct and retain the hardware identity if a slot is reused
    public static List<Device> connected(){
        List<Device> devices = new ArrayList<>();
        for(int slot = GLFW.GLFW_JOYSTICK_1; slot <= GLFW.GLFW_JOYSTICK_LAST; slot++){
            if(!GLFW.glfwJoystickPresent(slot)) continue;
            String guid = GLFW.glfwGetJoystickGUID(slot);
            String name = GLFW.glfwGetJoystickName(slot);
            devices.add(new Device(slot, (guid == null ? "unknown" : guid) + "/" + slot,
                    (name == null || name.isBlank() ? "Controller" : name) + " (" + (slot + 1) + ")",
                    GLFW.glfwJoystickIsGamepad(slot)));
        }
        return List.copyOf(devices);
    }

    // List selectable ids while keeping a disconnected selection available for display
    public static List<String> selectionIds(String selected){
        List<String> ids = new ArrayList<>();
        ids.add(HardwareControllerBindings.AUTOMATIC_DEVICE);
        connected().forEach(device -> ids.add(device.id()));
        if(selected != null && !selected.isEmpty() && !ids.contains(selected)) ids.add(selected);
        return List.copyOf(ids);
    }

    // Describe unavailable selections without switching their input source
    public static String label(String id){
        if(id == null || id.isEmpty() || HardwareControllerBindings.AUTOMATIC_DEVICE.equals(id)) return "Automatic";
        return connected().stream().filter(device -> device.id().equals(id)).map(Device::label).findFirst().orElse("Disconnected controller");
    }

    // Resolve automatic selection before listing the controls this device can actually report
    public static List<HardwareControllerBindings.BindingOption> bindingOptions(String selected){
        var devices = connected();
        var device = selected == null || selected.isEmpty() || HardwareControllerBindings.AUTOMATIC_DEVICE.equals(selected)
                ? automatic(devices) : devices.stream().filter(entry -> entry.id().equals(selected)).findFirst().orElse(null);
        return HardwareControllerBindings.options(values(device).keySet());
    }

    // Publish independent devices and the legacy automatic source from one polling pass
    public static Snapshot snapshot(){
        var devices = connected();
        var automatic = automatic(devices);
        Map<String, Double> values = new LinkedHashMap<>(), automaticValues = Map.of();
        for(var device : devices){
            var sampled = values(device);
            if(device.equals(automatic)) automaticValues = sampled;
            sampled.forEach((binding, val) -> values.put(HardwareControllerBindings.forDevice(binding, device.id()), val));
        }
        values.putAll(automaticValues);
        return new Snapshot(values, automaticValues);
    }

    // Preserve raw axes and buttons alongside mapped gamepad controls
    public static Map<String, Double> values(Device device){
        if(device == null || !GLFW.glfwJoystickPresent(device.slot())) return Map.of();
        String guid = GLFW.glfwGetJoystickGUID(device.slot());
        if(!device.id().equals((guid == null ? "unknown" : guid) + "/" + device.slot())) return Map.of();
        var axes = GLFW.glfwGetJoystickAxes(device.slot());
        var buttons = GLFW.glfwGetJoystickButtons(device.slot());
        double[] rawAxes = new double[axes == null ? 0 : Math.min(axes.remaining(), HardwareControllerBindings.MAX_RAW_AXES)];
        boolean[] rawButtons = new boolean[buttons == null ? 0 : Math.min(buttons.remaining(), HardwareControllerBindings.MAX_RAW_BUTTONS)];
        for(int idx = 0; idx < rawAxes.length; idx++) rawAxes[idx] = axes.get(axes.position() + idx);
        for(int idx = 0; idx < rawButtons.length; idx++) rawButtons[idx] = buttons.get(buttons.position() + idx) == GLFW.GLFW_PRESS;
        Map<String, Double> res = new LinkedHashMap<>();
        if(device.standardized()){
            try(var stack = MemoryStack.stackPush()){
                var state = GLFWGamepadState.malloc(stack);
                if(GLFW.glfwGetGamepadState(device.slot(), state)){
                    double[] mappedAxes = new double[6];
                    boolean[] mappedButtons = new boolean[15];
                    for(int idx = 0; idx < mappedAxes.length; idx++) mappedAxes[idx] = state.axes(idx);
                    for(int idx = 0; idx < mappedButtons.length; idx++) mappedButtons[idx] = state.buttons(idx) == GLFW.GLFW_PRESS;
                    res.putAll(HardwareControllerBindings.values(new HardwareControllerState(device.slot(), device.label(), true, mappedAxes, mappedButtons)));
                }
            }
        }
        res.putAll(HardwareControllerBindings.values(new HardwareControllerState(device.slot(), device.label(), false, rawAxes, rawButtons)));
        var hats = GLFW.glfwGetJoystickHats(device.slot());
        int[] directions = {GLFW.GLFW_HAT_UP, GLFW.GLFW_HAT_RIGHT, GLFW.GLFW_HAT_DOWN, GLFW.GLFW_HAT_LEFT};
        String[] labels = {"up", "right", "down", "left"};
        if(hats != null) for(int idx = 0; idx < Math.min(hats.remaining(), HardwareControllerBindings.MAX_RAW_HATS); idx++){
            int state = hats.get(hats.position() + idx);
            for(int direction = 0; direction < directions.length; direction++) res.put(
                    HardwareControllerBindings.PREFIX + "hat_" + idx + "_" + labels[direction], (state & directions[direction]) != 0 ? 1.0 : 0.0);
        }
        return Map.copyOf(res);
    }

    // Prefer mapped gamepads for legacy automatic bindings
    public static Device automatic(List<Device> devices){
        return devices.stream().filter(Device::standardized).findFirst().orElse(devices.isEmpty() ? null : devices.getFirst());
    }

    public record Snapshot(Map<String, Double> values, Map<String, Double> automatic){
        public Snapshot{
            values = Map.copyOf(values);
            automatic = Map.copyOf(automatic);
        }
    }

    public record Device(int slot, String id, String label, boolean standardized){}
}
