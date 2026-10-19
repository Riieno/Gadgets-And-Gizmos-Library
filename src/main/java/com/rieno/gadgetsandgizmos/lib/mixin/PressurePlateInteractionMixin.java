package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.interaction.BlockInteractionTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Attribute player pressure plate pulses before neighbor signals are notified
@Mixin(BasePressurePlateBlock.class)
public abstract class PressurePlateInteractionMixin{
    @Inject(method = "entityInside", at = @At("HEAD"))
    private void gadgetsngizmos$interact(BlockState state, Level level, BlockPos pos, Entity entity, CallbackInfo ci){
        if(entity instanceof ServerPlayer player) BlockInteractionTracker.record(level, pos, player);
    }
}
