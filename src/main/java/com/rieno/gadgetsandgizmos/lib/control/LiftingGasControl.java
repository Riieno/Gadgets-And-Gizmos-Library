package com.rieno.gadgetsandgizmos.lib.control;

// Control a gas provider's target volume without changing its configured maximum
public interface LiftingGasControl{
    // Apply an exact nonnegative target volume on the server thread
    void setControlledGasVolume(double volume);

    // Release transient control and resume the provider's ordinary input
    void clearControlledGasVolume();
}
