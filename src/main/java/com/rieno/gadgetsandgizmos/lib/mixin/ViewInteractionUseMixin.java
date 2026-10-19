package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.view.RemoteViewClient;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewInteractionClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Held use belongs to the target's drag handler and must not repeat ordinary right-clicks
@Mixin(Minecraft.class)
public abstract class ViewInteractionUseMixin{
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void cameraUse(CallbackInfo ci){ if(RemoteViewClient.isActive() && !ViewInteractionClient.replaying()) ci.cancel(); }
}
