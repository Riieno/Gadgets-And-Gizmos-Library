package com.rieno.gadgetsandgizmos.lib.client.view;

import net.minecraft.world.phys.Vec3;

// Isolate main-view post-processing while a smaller scene target is active
public interface ViewSceneAccess{
    // Suspend auxiliary targets and return their restoration action
    Runnable gadgetsngizmos$suspendViewTargets();
    // Position this renderer's terrain storage at the lens before culling
    void gadgetsngizmos$prepareView(Vec3 pos);
    // Release the terrain and sky buffers owned by this renderer
    void gadgetsngizmos$closeView();
}
