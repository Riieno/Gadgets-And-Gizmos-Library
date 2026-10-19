package com.rieno.gadgetsandgizmos.lib.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneWorldAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Route secondary world draws to the active capture framebuffer
@Mixin(Minecraft.class)
public abstract class ViewSceneTargetMixin implements ViewSceneWorldAccess{
    @Shadow @Final @Mutable public LevelRenderer levelRenderer;

    // Give secondary draws their own terrain renderer without replacing the player's camera
    @Override
    public LevelRenderer gadgetsngizmos$swapViewRenderer(LevelRenderer renderer){
        LevelRenderer prev = levelRenderer;
        levelRenderer = renderer;
        return prev;
    }

    // Render captured translucency directly into its own bounded framebuffer
    @Inject(method = "useShaderTransparency", at = @At("HEAD"), cancellable = true)
    private static void directCaptureTransparency(CallbackInfoReturnable<Boolean> cir){
        if(ViewSceneRenderer.isCapturing()) cir.setReturnValue(false);
    }
    // Preserve the normal target outside a bounded capture pass
    @Inject(method = "getMainRenderTarget", at = @At("HEAD"), cancellable = true)
    private void captureTarget(CallbackInfoReturnable<RenderTarget> cir){
        RenderTarget target = ViewSceneRenderer.captureTarget();
        if(target != null) cir.setReturnValue(target);
    }
    // Leave the player's queued entity and block entity geometry untouched during captures
    @Inject(method = "renderBuffers", at = @At("HEAD"), cancellable = true)
    private void captureBuffers(CallbackInfoReturnable<RenderBuffers> cir){
        RenderBuffers buffers = ViewSceneRenderer.captureBuffers();
        if(buffers != null) cir.setReturnValue(buffers);
    }
}
