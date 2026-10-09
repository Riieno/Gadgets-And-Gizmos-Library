package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Retain native terrain in bounded feeds when Voxy replaces the player's nearby chunks
@Pseudo
@Mixin(targets = "me.cortex.voxy.client.VoxyClient", remap = false)
public abstract class ViewSceneVoxyMixin{
    // Keep secondary Sodium meshes drawable without changing Voxy's saved settings
    @Inject(method = "disableSodiumChunkRender", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void retainCaptureTerrain(CallbackInfoReturnable<Boolean> cir){
        if(ViewSceneRenderer.isCapturing()) cir.setReturnValue(false);
    }
}
