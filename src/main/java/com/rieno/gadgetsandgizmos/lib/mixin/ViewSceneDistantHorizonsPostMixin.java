package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Keep full-screen post processing and player temporal history outside small LOD feeds
@Pseudo
@Mixin(targets = {
        "com.seibel.distanthorizons.common.render.openGl.postProcessing.ssao.GlDhSSAORenderer",
        "com.seibel.distanthorizons.common.render.openGl.postProcessing.ssao.GlDhSSAORenderer_neoforge",
        "com.seibel.distanthorizons.common.render.openGl.postProcessing.antialiasing.GlDhTaaRenderer",
        "com.seibel.distanthorizons.common.render.openGl.postProcessing.antialiasing.GlDhTaaRenderer_neoforge",
        "com.seibel.distanthorizons.common.render.openGl.postProcessing.fog.GlDhFogRenderer",
        "com.seibel.distanthorizons.common.render.openGl.postProcessing.fog.GlDhFogRenderer_neoforge"
}, remap = false)
public abstract class ViewSceneDistantHorizonsPostMixin{
    // Leave native LOD geometry compositing active without rerunning expensive effects per camera
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void skipCapturePost(CallbackInfo ci){ if(ViewSceneRenderer.isCapturing()) ci.cancel(); }
}
