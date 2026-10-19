package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.view.ViewInteractionContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Grip discovery uses physical body distance in Simulated; admit the authenticated lens target
@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.blocks.throttle_lever.ThrottleLeverClientGripHandler", remap = false)
public abstract class ViewInteractionThrottleGripMixin{
    @Inject(method = "isInvalid", at = @At("HEAD"), cancellable = true)
    private static void lensGrip(@Coerce BlockEntity entity, CallbackInfoReturnable<Boolean> cir){
        var player = Minecraft.getInstance().player;
        if(player != null && ViewInteractionContext.cursor(player) != null)
            cir.setReturnValue(entity.isRemoved() || !ViewInteractionContext.canUse(player, entity));
    }
}
