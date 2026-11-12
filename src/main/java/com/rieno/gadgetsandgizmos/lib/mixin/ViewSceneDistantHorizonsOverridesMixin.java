package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Retain registered shader overrides while using native LOD attachments in a feed
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.coreapi.DependencyInjection.OverrideInjector", remap = false)
public abstract class ViewSceneDistantHorizonsOverridesMixin{
    @Inject(method = "get", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void nativeCaptureOverrides(CallbackInfoReturnable<Object> cir){
        Object val = cir.getReturnValue();
        if(ViewSceneRenderer.isCapturing() && val != null && val.getClass().getName().startsWith("net.irisshaders.iris."))
            cir.setReturnValue(null);
    }
}
