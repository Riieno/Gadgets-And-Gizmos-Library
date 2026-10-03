package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.function.Predicate;

// Restrict a machine port to the selected recipe without changing its inventory
public record WorkerItemPort(IItemHandler inventory, Predicate<ItemStack> accepts, int maximum) implements IItemHandler{
    public WorkerItemPort(IItemHandler inventory, Predicate<ItemStack> accepts){ this(inventory, accepts, Integer.MAX_VALUE); }
    @Override public int getSlots(){ return inventory.getSlots(); }
    @Override public ItemStack getStackInSlot(int slot){ return inventory.getStackInSlot(slot); }
    @Override public int getSlotLimit(int slot){ return inventory.getSlotLimit(slot); }
    @Override public boolean isItemValid(int slot, ItemStack stack){
        return accepts.test(stack) && inventory.isItemValid(slot, stack);
    }
    @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate){
        if(!accepts.test(stack)) return stack;
        int offered = Math.min(stack.getCount(), Math.max(0, maximum - inventory.getStackInSlot(slot).getCount()));
        if(offered == 0) return stack;
        ItemStack remainder = inventory.insertItem(slot, stack.copyWithCount(offered), simulate);
        return stack.copyWithCount(stack.getCount() - offered + remainder.getCount());
    }
    @Override public ItemStack extractItem(int slot, int amount, boolean simulate){
        return inventory.extractItem(slot, amount, simulate);
    }
}
