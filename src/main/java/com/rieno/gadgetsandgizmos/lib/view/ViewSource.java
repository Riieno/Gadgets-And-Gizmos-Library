package com.rieno.gadgetsandgizmos.lib.view;

// Supply a live view without exposing a consuming mod's block entity class
public interface ViewSource{
    // Resolve the current world-space lens pose
    ViewPose viewPose(float partialTick);
    // Apply mouse motion and scroll zoom on the owning server thread
    void controlView(double panDelta, double tiltDelta, double zoomSteps);
    // Notify the source when exclusive mouse control begins or ends
    default void setViewControlled(boolean controlled){}
    // Refresh client effects even when a visible remote source is outside the ticking area
    default void updateViewEffects(float partialTick){}
    // Check whether the source can still provide a view
    boolean isViewAvailable();
}
