package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Apply after Entity Culling adds its visibility methods to entities and block entities
@Mixin(value = {Entity.class, BlockEntity.class}, priority = 900)
public abstract class ViewSceneEntityCullingMixin{
    // Retain lens frustum and depth tests instead of reusing the player's occlusion result
    @Inject(method = "isCulled", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void captureVisibility(CallbackInfoReturnable<Boolean> cir){
        if(ViewSceneRenderer.isCapturing()) cir.setReturnValue(false);
    }

    // Leave the player's visibility history untouched by secondary scene draws
    @Inject(method = "setOutOfCamera", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void retainPlayerVisibility(boolean val, CallbackInfo ci){
        if(ViewSceneRenderer.isCapturing()) ci.cancel();
    }
}
