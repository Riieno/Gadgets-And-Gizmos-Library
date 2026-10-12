package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Keep camera geometry in its owned buffers before optional renderers replace their getters
@Mixin(value = RenderBuffers.class, priority = 2000)
public abstract class ViewSceneRenderBuffersMixin{
    @Shadow @Final private MultiBufferSource.BufferSource bufferSource;
    @Shadow @Final private MultiBufferSource.BufferSource crumblingBufferSource;

    // Flush camera entities through the normal layer order while their view matrix is active
    @Inject(method = "bufferSource", at = @At("HEAD"), cancellable = true)
    private void captureEntities(CallbackInfoReturnable<MultiBufferSource.BufferSource> cir){
        if(ViewSceneRenderer.captureBuffers() == (Object) this) cir.setReturnValue(bufferSource);
    }

    // Keep block damage in the same scene without an optional unflushable wrapper
    @Inject(method = "crumblingBufferSource", at = @At("HEAD"), cancellable = true)
    private void captureDamage(CallbackInfoReturnable<MultiBufferSource.BufferSource> cir){
        if(ViewSceneRenderer.captureBuffers() == (Object) this) cir.setReturnValue(crumblingBufferSource);
    }
}
