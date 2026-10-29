package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

// Retain one block entity's local geometry while preserving its live presentation state
public interface RetainedHologramRenderer extends AutoCloseable{
    void render(float partialTick, PoseStack poses, MultiBufferSource buffers, int light, int overlay);
    @Override default void close(){}
}
