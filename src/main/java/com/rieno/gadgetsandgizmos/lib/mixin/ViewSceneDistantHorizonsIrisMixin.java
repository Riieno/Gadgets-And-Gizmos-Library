package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Use DH's native pass alongside the feed's existing unshaded terrain pipeline
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.neoforge.wrappers.modAccessor.IrisAccessor", remap = false)
public abstract class ViewSceneDistantHorizonsIrisMixin{
    // Keep player shader attachments and reversed depth out of secondary LOD draws
    @Inject(method = {"isShaderPackInUse", "isRenderingShadowPass", "isReverseZDuringShaders"},
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void nativeCapture(CallbackInfoReturnable<Boolean> cir){
        if(ViewSceneRenderer.isCapturing()) cir.setReturnValue(false);
    }
}
