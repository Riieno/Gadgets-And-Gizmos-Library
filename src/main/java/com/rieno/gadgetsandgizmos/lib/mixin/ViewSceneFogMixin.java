package com.rieno.gadgetsandgizmos.lib.mixin;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Keep bounded camera terrain faded even when LOD mods remove the main-view fog wall
@Mixin(value = FogRenderer.class, priority = 900)
public abstract class ViewSceneFogMixin{
    // Apply the source's distance after vanilla and NeoForge fog callbacks finish
    @Inject(method = "setupFog", at = @At("TAIL"))
    private static void boundCaptureFog(Camera camera, FogRenderer.FogMode mode, float distance,
                                        boolean foggy, float partialTick, CallbackInfo ci){
        if(!ViewSceneRenderer.isCapturing() || mode != FogRenderer.FogMode.FOG_TERRAIN) return;
        float end = Math.min(RenderSystem.getShaderFogEnd(), ViewSceneRenderer.captureDistance() * 0.94F);
        RenderSystem.setShaderFogEnd(end);
        RenderSystem.setShaderFogStart(Math.min(RenderSystem.getShaderFogStart(), end * 0.75F));
        RenderSystem.setShaderFogShape(FogShape.SPHERE);
    }
}
