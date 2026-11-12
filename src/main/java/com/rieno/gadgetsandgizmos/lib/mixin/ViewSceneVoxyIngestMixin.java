package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Camera chunk storage borrows existing terrain instead of submitting duplicate ingest work
@Pseudo
@Mixin(targets = "me.cortex.voxy.common.world.service.VoxelIngestService", remap = false)
public abstract class ViewSceneVoxyIngestMixin{
    @Inject(method = "tryAutoIngestChunk", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void retainWorldIngest(CallbackInfoReturnable<Boolean> cir){
        if(ViewSceneRenderer.isCapturing()) cir.setReturnValue(false);
    }
}
