package com.rieno.gadgetsandgizmos.lib.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Traverse uploaded terrain for a camera without issuing world-generation or geometry requests
@Pseudo
@Mixin(targets = "me.cortex.voxy.client.core.rendering.hierachical.HierarchicalOcclusionTraverser", remap = false)
public abstract class ViewSceneVoxyTraversalMixin{
    @WrapOperation(method = "uploadUniform", at = @At(value = "INVOKE",
            target = "Lme/cortex/voxy/client/core/rendering/building/RenderGenerationService;getTaskCount()I"), require = 0, remap = false)
    private int retainLoadedGeometry(@Coerce Object service, Operation<Integer> original){
        return ViewSceneRenderer.isCapturing() ? 4000 : original.call(service);
    }

    @Inject(method = "downloadResetRequestQueue", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void skipCameraRequests(CallbackInfo ci){ if(ViewSceneRenderer.isCapturing()) ci.cancel(); }
}
