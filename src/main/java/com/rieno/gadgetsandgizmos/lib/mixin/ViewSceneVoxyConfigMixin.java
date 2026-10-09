package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Keep Voxy's large LOD engine attached to the main view instead of duplicating it per feed
@Pseudo
@Mixin(targets = "me.cortex.voxy.client.config.VoxyConfig", remap = false)
public abstract class ViewSceneVoxyConfigMixin{
    // Decline secondary LOD allocation and draws without modifying saved rendering settings
    @Inject(method = "isRenderingEnabled", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void keepMainLodEngine(CallbackInfoReturnable<Boolean> cir){
        if(ViewSceneRenderer.isCapturing()) cir.setReturnValue(false);
    }
}
