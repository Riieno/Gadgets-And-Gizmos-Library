package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Keep secondary terrain renderers from allocating another Voxy world and geometry cache
@Pseudo
@Mixin(targets = "me.cortex.voxy.client.config.VoxyConfig", remap = false)
public abstract class ViewSceneVoxyConfigMixin{
    @Inject(method = "isRenderingEnabled", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void shareMainTerrain(CallbackInfoReturnable<Boolean> cir){
        if(ViewSceneRenderer.isCapturing()) cir.setReturnValue(false);
    }
}
