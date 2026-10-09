package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.create.encasing.CreateCasingApi;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Accept registered casing materials on Create belts
@Mixin(BeltBlock.class)
public abstract class BeltCasingInteractionMixin{
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void tryCasing(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                           InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<ItemInteractionResult> cir){
        ItemInteractionResult res = CreateCasingApi.tryBeltCasing(stack, state, level, pos, player);
        if(res == ItemInteractionResult.SUCCESS) cir.setReturnValue(res);
    }
}
