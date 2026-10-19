package com.rieno.gadgetsandgizmos.lib.view;

// Expose source settings to reusable remote control surfaces
public interface ControlledViewSource extends ViewSource{
    // Read the effective settings for the current source
    ViewControlState viewControlState();
    // Apply validated settings on the owning server thread
    void applyViewSettings(ViewControlState state);
}
