package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.inventory.ContainerAccessRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Apply registered policies at the common NeoForge item-capability boundary
@Mixin(value = BlockCapability.class, remap = false)
public abstract class ContainerItemCapabilityMixin{
    @Inject(method = "getCapability", at = @At("RETURN"), cancellable = true)
    private void gadgetsngizmos$guardItems(Level level, BlockPos pos, BlockState state, BlockEntity be, Object ctx, CallbackInfoReturnable<Object> cir){
        if((Object) this == Capabilities.ItemHandler.BLOCK && cir.getReturnValue() instanceof IItemHandler handler){
            cir.setReturnValue(ContainerAccessRegistry.wrap(level, pos, handler));
        }
    }
}
