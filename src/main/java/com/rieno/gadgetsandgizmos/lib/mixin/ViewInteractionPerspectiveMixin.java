package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.view.ViewInteractionContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Native interaction math uses the lens while the player's physical body stays in place
@Mixin(Entity.class)
public abstract class ViewInteractionPerspectiveMixin{
    @Inject(method = {"getEyePosition()Lnet/minecraft/world/phys/Vec3;", "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;"}, at = @At("HEAD"), cancellable = true)
    private void lensEye(CallbackInfoReturnable<Vec3> cir){
        if(!((Object)this instanceof Player player)) return;
        var cursor = ViewInteractionContext.perspective(player);
        if(cursor != null) cir.setReturnValue(cursor.pose().position());
    }
    @Inject(method = "getViewVector", at = @At("HEAD"), cancellable = true)
    private void lensDirection(float partialTick, CallbackInfoReturnable<Vec3> cir){
        if(!((Object)this instanceof Player player)) return;
        var cursor = ViewInteractionContext.perspective(player);
        if(cursor != null) cir.setReturnValue(cursor.pose().forward());
    }
}
