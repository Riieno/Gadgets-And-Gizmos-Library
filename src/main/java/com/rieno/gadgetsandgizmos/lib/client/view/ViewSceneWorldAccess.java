package com.rieno.gadgetsandgizmos.lib.client.view;

import net.minecraft.client.renderer.LevelRenderer;

// Select the world renderer used by client render integrations for one scene
public interface ViewSceneWorldAccess{
    // Return the previous renderer so the caller can restore it in a finally block
    LevelRenderer gadgetsngizmos$swapViewRenderer(LevelRenderer renderer);
}
