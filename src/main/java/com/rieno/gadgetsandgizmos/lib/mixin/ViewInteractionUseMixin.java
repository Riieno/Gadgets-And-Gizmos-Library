package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.view.RemoteViewClient;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewInteractionClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Held use belongs to the target's drag handler and must not repeat ordinary right-clicks
@Mixin(Minecraft.class)
public abstract class ViewInteractionUseMixin{
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void cameraUse(CallbackInfo ci){ if(RemoteViewClient.isActive() && !ViewInteractionClient.replaying()) ci.cancel(); }
    // Keep camera clicks from starting a local block-breaking prediction
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void cameraAttack(CallbackInfoReturnable<Boolean> cir){
        if(RemoteViewClient.isActive()) cir.setReturnValue(false);
    }
    // Clear held attack without progressing block damage
    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void cameraHeldAttack(boolean held, CallbackInfo ci){
        if(RemoteViewClient.isActive()) ci.cancel();
    }
}
