package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Keep Distant Horizons' global terrain and shader passes out of independent camera feeds
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.api.internal.ClientApi", remap = false)
public abstract class ViewSceneDistantHorizonsMixin{
    // Support both the older argument-based renderer and current shared-state entry points
    @Inject(method = {"renderLods", "renderDeferredLods", "renderDeferredLodsForShaders",
            "renderFadeOpaque", "renderFadeTransparent"}, at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void keepMainLodPasses(CallbackInfo ci){
        if(ViewSceneRenderer.isCapturing()) ci.cancel();
    }
}
