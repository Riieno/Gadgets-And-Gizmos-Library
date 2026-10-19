package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import foundry.veil.api.client.render.VeilRenderer;
import foundry.veil.api.client.render.dynamicbuffer.DynamicBufferType;
import net.minecraft.resources.ResourceLocation;

// Require active scene buffers before a renderer uses their textures
public final class DynamicRenderBuffers{
    private DynamicRenderBuffers(){}

    // Request the owner's buffers and wait for a later frame when their shaders or targets change
    public static boolean enableForFrame(VeilRenderer renderer, ResourceLocation owner, DynamicBufferType... buffers){
        RenderSystem.assertOnRenderThreadOrInit();
        if(renderer.enableBuffers(owner, buffers)) return false;
        int required = DynamicBufferType.encode(buffers);
        return (renderer.getActiveBuffers() & required) == required;
    }
}
