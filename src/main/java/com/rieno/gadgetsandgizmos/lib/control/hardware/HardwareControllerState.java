package com.rieno.gadgetsandgizmos.lib.control.hardware;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.Arrays;

// Store one safe snapshot of a joystick or gamepad without platform specific objects
public record HardwareControllerState(int deviceId, String name, boolean standardized,
                                      double[] axes, boolean[] buttons) {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the hardware controller state
    public HardwareControllerState {
        name = name == null ? "" : name;
        axes = axes == null ? new double[0] : Arrays.copyOf(axes, axes.length);
        buttons = buttons == null ? new boolean[0] : Arrays.copyOf(buttons, buttons.length);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the axes
    @Override
    public double[] axes() {
        return Arrays.copyOf(axes, axes.length);
    }

    // Get the buttons
    @Override
    public boolean[] buttons() {
        return Arrays.copyOf(buttons, buttons.length);
    }

    // Get the axis
    public double axis(int index) {
        return index < 0 || index >= axes.length || !Double.isFinite(axes[index])
                ? 0.0D : Math.max(-1.0D, Math.min(1.0D, axes[index]));
    }

    // Check if the button is pressed
    public boolean button(int index) {
        return index >= 0 && index < buttons.length && buttons[index];
    }
}
