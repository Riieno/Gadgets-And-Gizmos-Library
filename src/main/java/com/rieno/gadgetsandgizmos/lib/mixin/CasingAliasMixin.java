package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.create.encasing.CreateCasingApi;
import com.simibubi.create.content.decoration.encasing.EncasableBlock;
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

// Try registered aliases after native encasing variants
@Mixin(EncasableBlock.class)
public interface CasingAliasMixin{
    @Inject(method = "tryEncase", at = @At("RETURN"), cancellable = true)
    private void tryAlias(BlockState state, Level level, BlockPos pos, ItemStack stack, Player player,
                          InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<ItemInteractionResult> cir){
        if(cir.getReturnValue() != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) return;
        cir.setReturnValue(CreateCasingApi.tryAlias(state, level, pos, stack, player, hand, hit));
    }
}
