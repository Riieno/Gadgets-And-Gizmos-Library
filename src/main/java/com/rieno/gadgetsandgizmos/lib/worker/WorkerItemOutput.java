package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import java.util.function.Predicate;

// Expose only completed products without withdrawing an unfinished processing input
public record WorkerItemOutput(IItemHandler inventory, Predicate<ItemStack> finished) implements IItemHandler{
    @Override public int getSlots(){ return inventory.getSlots(); }
    @Override public int getSlotLimit(int slot){ return inventory.getSlotLimit(slot); }
    @Override public boolean isItemValid(int slot, ItemStack stack){ return false; }
    @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate){ return stack; }
    @Override public ItemStack getStackInSlot(int slot){
        ItemStack stack = inventory.getStackInSlot(slot);
        return finished.test(stack) ? stack : ItemStack.EMPTY;
    }
    @Override public ItemStack extractItem(int slot, int amount, boolean simulate){
        return finished.test(inventory.getStackInSlot(slot)) ? inventory.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
    }
}
