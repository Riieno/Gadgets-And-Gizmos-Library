package com.rieno.gadgetsandgizmos.lib.control.hardware;

import com.rieno.gadgetsandgizmos.lib.client.control.HardwareControllers;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Keep selected devices independent while retaining legacy automatic controls
class HardwareDeviceSelectionTest{
    @Test void availableOptionsContainOnlyTheDevicesReportedControls(){
        var wheel = HardwareControllerBindings.options(List.of("hardware:axis_0", "hardware:axis_1",
                "hardware:button_0", "hardware:hat_0_up"));
        assertEquals(List.of("hardware:axis_0", "hardware:axis_1", "hardware:button_0", "hardware:hat_0_up"),
                wheel.stream().map(HardwareControllerBindings.BindingOption::id).toList());
        assertEquals("Controller Axis 0", wheel.getFirst().label());
        var mapped = HardwareControllerBindings.options(HardwareControllerBindings.values(
                new HardwareControllerState(0, "Gamepad", true, new double[6], new boolean[15])).keySet());
        assertTrue(mapped.stream().anyMatch(option -> option.id().equals("hardware:left_x")));
        assertFalse(mapped.stream().anyMatch(option -> option.id().equals("hardware:axis_6")));
        assertFalse(mapped.stream().anyMatch(option -> option.id().equals("hardware:button_15")));
        assertTrue(HardwareControllerBindings.options(List.of()).isEmpty());
    }

    @Test void identicalInputsOnDifferentDevicesHaveIndependentBindings(){
        String input = HardwareControllerBindings.PREFIX + "axis_0";
        assertEquals(input, HardwareControllerBindings.forDevice(input, ""));
        assertEquals(input, HardwareControllerBindings.forDevice(input, HardwareControllerBindings.AUTOMATIC_DEVICE));
        assertNotEquals(HardwareControllerBindings.forDevice(input, "wheel/0"), HardwareControllerBindings.forDevice(input, "stick/1"));
        assertTrue(HardwareControllerBindings.isHardwareBinding(HardwareControllerBindings.forDevice(input, "wheel/0")));
        assertEquals("key.jump", HardwareControllerBindings.forDevice("key.jump", "wheel/0"));
    }

    @Test void automaticSelectionPrefersMappedGamepadsAndHandlesDisconnection(){
        var wheel = new HardwareControllers.Device(0, "wheel/0", "Wheel", false);
        var gamepad = new HardwareControllers.Device(1, "pad/1", "Gamepad", true);
        assertEquals(gamepad, HardwareControllers.automatic(List.of(wheel, gamepad)));
        assertEquals(wheel, HardwareControllers.automatic(List.of(wheel)));
        assertNull(HardwareControllers.automatic(List.of()));
    }
}
