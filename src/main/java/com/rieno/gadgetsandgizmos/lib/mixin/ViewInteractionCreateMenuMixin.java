package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.view.ViewBlockInteractions;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Create menus use their block entity's reach check instead of the vanilla player helper
@Mixin(SmartBlockEntity.class)
public abstract class ViewInteractionCreateMenuMixin{
    @Inject(method = "canPlayerUse", at = @At("HEAD"), cancellable = true)
    private void remoteMenuReach(Player player, CallbackInfoReturnable<Boolean> cir){
        var entity = (SmartBlockEntity)(Object)this;
        if(com.rieno.gadgetsandgizmos.lib.view.ViewInteractionContext.canUse(player, entity)
                || player instanceof ServerPlayer server && ViewBlockInteractions.menuReach(server, entity.getBlockPos())) cir.setReturnValue(true);
    }
}
