package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.view.ViewInteractionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Simulated's throttle and wheel handlers use attribute reach instead of Player's reach method
@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.util.hold_interaction.BlockHoldInteraction", remap = false)
public abstract class ViewInteractionHoldMixin{
    @Inject(method = "getInteractionRange", at = @At("HEAD"), cancellable = true)
    private static void lensRange(Player player, CallbackInfoReturnable<Double> cir){
        var cursor = ViewInteractionContext.cursor(player);
        if(cursor != null) cir.setReturnValue(cursor.range());
    }
    @Inject(method = "inInteractionRange(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/Position;D)Z", at = @At("HEAD"), cancellable = true)
    private static void targetReach(Player player, Position pos, double padding, CallbackInfoReturnable<Boolean> cir){
        if(ViewInteractionContext.cursor(player) != null) cir.setReturnValue(ViewInteractionContext.canUse(player, BlockPos.containing(pos.x(), pos.y(), pos.z())));
    }
    @Inject(method = "inInteractionRange(Lnet/minecraft/world/entity/player/Player;Lorg/joml/Vector3dc;D)Z", at = @At("HEAD"), cancellable = true)
    private static void targetVectorReach(Player player, Vector3dc pos, double padding, CallbackInfoReturnable<Boolean> cir){
        if(ViewInteractionContext.cursor(player) != null) cir.setReturnValue(ViewInteractionContext.canUse(player, BlockPos.containing(pos.x(), pos.y(), pos.z())));
    }
}
