package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Keep global shader LOD lookups from reusing the player's temporal viewport in a feed
@Pseudo
@Mixin(targets = "me.cortex.voxy.client.core.VoxyRenderSystem", remap = false)
public abstract class ViewSceneVoxyRenderSystemMixin{
    // Decline a secondary viewport before its matrices or occlusion buffers are changed
    @Inject(method = {"getViewport", "setupViewport"}, at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void keepMainViewport(CallbackInfoReturnable<Object> cir){
        if(ViewSceneRenderer.isCapturing()) cir.setReturnValue(null);
    }
}
