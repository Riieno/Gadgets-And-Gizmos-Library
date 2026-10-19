package com.rieno.gadgetsandgizmos.lib.view;

// Supply a live view without exposing a consuming mod's block entity class
public interface ViewSource{
    // Resolve the current world-space lens pose
    ViewPose viewPose(float partialTick);
    // Let sources fit a tracked target to the actual capture shape while preserving existing providers
    default ViewPose viewPose(float partialTick, double aspect){ return viewPose(partialTick); }
    // Apply mouse motion and scroll zoom on the owning server thread
    void controlView(double panDelta, double tiltDelta, double zoomSteps);
    // Notify the source when exclusive mouse control begins or ends
    default void setViewControlled(boolean controlled){}
    // Refresh client effects even when a visible remote source is outside the ticking area
    default void updateViewEffects(float partialTick){}
    // Identify the entity whose own head is omitted from its first-person capture
    default net.minecraft.world.entity.Entity viewEntity(){ return null; }
    // Check whether the source can still provide a view
    boolean isViewAvailable();
}
