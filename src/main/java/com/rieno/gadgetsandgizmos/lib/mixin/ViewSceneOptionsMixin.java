package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Bound secondary terrain storage without changing the player's saved options
@Mixin(Options.class)
public abstract class ViewSceneOptionsMixin{
    // Use the feed's render distance only while its renderer is selected
    @Inject(method = "getEffectiveRenderDistance", at = @At("RETURN"), cancellable = true)
    private void viewRenderDistance(CallbackInfoReturnable<Integer> cir){
        if(ViewSceneRenderer.isCapturing()){
            cir.setReturnValue(Math.min(cir.getReturnValue(), (int) (ViewSceneRenderer.captureDistance() / 16)));
        }
    }
}
