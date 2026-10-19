package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Keep ship meshes and visibility separate for each secondary scene
@Mixin(ClientSubLevel.class)
public abstract class ViewSceneSubLevelMixin{
    // Select the feed's render data without replacing the player's stored render data
    @Inject(method = "getRenderData", at = @At("RETURN"), cancellable = true)
    private void viewRenderData(CallbackInfoReturnable<SubLevelRenderData> cir){
        if(ViewSceneRenderer.isCapturing() && cir.getReturnValue() != null){
            cir.setReturnValue(ViewSceneRenderer.subLevelData((ClientSubLevel) (Object) this, cir.getReturnValue()));
        }
    }
    // Recreate feed meshes when Sable resizes or replaces a ship's render data
    @Inject(method = "updateRenderData", at = @At("HEAD"))
    private void updateViewData(CallbackInfo ci){
        ViewSceneRenderer.invalidateSubLevel((ClientSubLevel) (Object) this);
    }
}
