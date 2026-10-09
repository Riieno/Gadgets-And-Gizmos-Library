package com.rieno.gadgetsandgizmos.lib.client.view;

import com.rieno.gadgetsandgizmos.lib.view.ViewPose;

// Apply a complete lens pose to a client camera
public interface ViewCameraAccess{
    // Update position, rotation and the camera's derived basis together
    void gadgetsngizmos$applyView(ViewPose pose);
}
