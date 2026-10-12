package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.item.TimedItemTransformations;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Enrol only handlers which actually contain timed items.
@Mixin(ItemStackHandler.class)
public abstract class TimedItemHandlerMixin{
    @Inject(method = "getStackInSlot", at = @At("RETURN"))
    private void gadgetsngizmos$observeRead(int slot, CallbackInfoReturnable<ItemStack> cir){
        TimedItemTransformations.observe((ItemStackHandler)(Object)this, slot, cir.getReturnValue());
    }

    @Inject(method = "setStackInSlot", at = @At("RETURN"))
    private void gadgetsngizmos$observeWrite(int slot, ItemStack stack, CallbackInfo ci){
        ItemStackHandler self = (ItemStackHandler)(Object)this;
        TimedItemTransformations.observe(self, slot, self.getStackInSlot(slot));
    }

    @Inject(method = "insertItem", at = @At("RETURN"))
    private void gadgetsngizmos$observeInsert(int slot, ItemStack stack, boolean simulate,
            CallbackInfoReturnable<ItemStack> cir){
        if(simulate) return;
        ItemStackHandler self = (ItemStackHandler)(Object)this;
        TimedItemTransformations.observe(self, slot, self.getStackInSlot(slot));
    }

    @Inject(method = "deserializeNBT(Lnet/minecraft/core/HolderLookup$Provider;Lnet/minecraft/nbt/CompoundTag;)V",
            at = @At("RETURN"))
    private void gadgetsngizmos$observeLoad(HolderLookup.Provider registries, CompoundTag tag, CallbackInfo ci){
        ItemStackHandler self = (ItemStackHandler)(Object)this;
        for(int slot = 0; slot < self.getSlots(); slot++){
            TimedItemTransformations.observe(self, slot, self.getStackInSlot(slot));
        }
    }
}
