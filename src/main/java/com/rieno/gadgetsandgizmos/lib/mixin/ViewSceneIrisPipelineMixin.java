package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewShaderCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

// Isolate Iris shader processing to the main view while retaining the loaded pack
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.PipelineManager", remap = false)
public abstract class ViewSceneIrisPipelineMixin{
    // Avoid constructing a second shader pack pipeline for a display source
    @Inject(method = "preparePipeline", at = @At("HEAD"),
            cancellable = true, require = 0, remap = false)
    private void prepareCapturePipeline(CallbackInfoReturnable<Object> cir){
        if(ViewSceneRenderer.isCapturing()) cir.setReturnValue(ViewShaderCompat.capturePipeline(this));
    }
    // Substitute the unshaded pipeline only for the active display capture
    @Inject(method = "getPipelineNullable", at = @At("RETURN"),
            cancellable = true, require = 0, remap = false)
    private void capturePipeline(CallbackInfoReturnable<Object> cir){
        if(ViewSceneRenderer.isCapturing() && cir.getReturnValue() != null){
            cir.setReturnValue(ViewShaderCompat.capturePipeline(cir.getReturnValue()));
        }
    }
    // Keep shader override lookups consistent with the secondary pipeline
    @Inject(method = "getPipeline", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void captureOptionalPipeline(CallbackInfoReturnable<Optional<?>> cir){
        if(ViewSceneRenderer.isCapturing()){
            cir.setReturnValue(cir.getReturnValue().map(ViewShaderCompat::capturePipeline));
        }
    }
}
