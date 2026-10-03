package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.inventory.ContainerAccessRegistry;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Keep vanilla hopper insertion subject to the same policies as capability transfers
@Mixin(HopperBlockEntity.class)
public abstract class ContainerHopperAccessMixin{
    @Inject(method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/Container;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Direction;)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"), cancellable = true)
    private static void gadgetsngizmos$guardTransfer(Container source, Container target, ItemStack stack, Direction side, CallbackInfoReturnable<ItemStack> cir){
        if(!ContainerAccessRegistry.canTransfer(target, stack, true) || !ContainerAccessRegistry.canTransfer(source, stack, false)) cir.setReturnValue(stack);
    }
}
