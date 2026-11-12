package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Keep world uploads, eviction and asynchronous downloads on Voxy's main render pass
@Pseudo
@Mixin(targets = {"me.cortex.voxy.client.core.rendering.hierachical.AsyncNodeManager",
        "me.cortex.voxy.client.core.rendering.hierachical.NodeCleaner",
        "me.cortex.voxy.client.core.rendering.util.DownloadStream"}, remap = false)
public abstract class ViewSceneVoxyMaintenanceMixin{
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void retainWorldUpdates(CallbackInfo ci){ if(ViewSceneRenderer.isCapturing()) ci.cancel(); }
}
