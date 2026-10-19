package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.view.ViewInteractionClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Custom drag handlers continue to observe use while ordinary body controls are released
@Mixin(KeyMapping.class)
public abstract class ViewInteractionKeyMixin{
    @Inject(method = "isDown", at = @At("HEAD"), cancellable = true)
    private void cameraUseHeld(CallbackInfoReturnable<Boolean> cir){
        if((Object)this == Minecraft.getInstance().options.keyUse && ViewInteractionClient.held()) cir.setReturnValue(true);
    }
}
