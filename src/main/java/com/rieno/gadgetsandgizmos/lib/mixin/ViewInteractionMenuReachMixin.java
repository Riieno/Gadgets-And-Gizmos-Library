package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.view.ViewBlockInteractions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Keep an explicitly opened camera menu usable without moving the player's body
@Mixin(Player.class)
public abstract class ViewInteractionMenuReachMixin{
    @Inject(method = "canInteractWithBlock", at = @At("HEAD"), cancellable = true)
    private void remoteMenuReach(BlockPos pos, double padding, CallbackInfoReturnable<Boolean> cir){
        if((Object)this instanceof Player player && (com.rieno.gadgetsandgizmos.lib.view.ViewInteractionContext.canUse(player, pos)
                || player instanceof ServerPlayer server && ViewBlockInteractions.menuReach(server, pos))) cir.setReturnValue(true);
    }
    @Inject(method = "blockInteractionRange", at = @At("HEAD"), cancellable = true)
    private void lensRange(CallbackInfoReturnable<Double> cir){
        var cursor = com.rieno.gadgetsandgizmos.lib.view.ViewInteractionContext.perspective((Player)(Object)this);
        if(cursor != null) cir.setReturnValue(cursor.range());
    }
}
