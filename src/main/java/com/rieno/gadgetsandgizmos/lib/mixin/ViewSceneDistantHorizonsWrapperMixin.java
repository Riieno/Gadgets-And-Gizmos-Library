package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Resolve DH's native render target and FOV from the secondary camera rather than the player
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftRenderWrapper_neoforge", remap = false)
public abstract class ViewSceneDistantHorizonsWrapperMixin{
    // Keep compositing inside the capture even when DH retained the player's last framebuffer ID
    @Inject(method = "getTargetFramebuffer", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void captureFramebuffer(CallbackInfoReturnable<Integer> cir){
        var target = ViewSceneRenderer.captureTarget();
        if(target != null) cir.setReturnValue(target.frameBufferId);
    }

    // Match native LOD projection to the selected camera lens
    @Inject(method = "getFovSetting", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void captureFov(CallbackInfoReturnable<Double> cir){
        var pose = ViewSceneRenderer.capturePose();
        if(pose != null) cir.setReturnValue(pose.fov());
    }
}
