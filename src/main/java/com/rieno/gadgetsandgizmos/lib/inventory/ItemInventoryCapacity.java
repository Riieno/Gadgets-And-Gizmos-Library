package com.rieno.gadgetsandgizmos.lib.inventory;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.util.function.Predicate;

// Count insertable items across an inventory without changing its contents
public final class ItemInventoryCapacity{
    private ItemInventoryCapacity(){}

    // Respect item components, stack sizes, slot limits and insertion filters up to the requested amount
    public static long insertable(IItemHandler inventory, ItemStack template, long maximum){
        if(inventory == null || template == null || template.isEmpty() || maximum <= 0L) return 0L;
        long amount = 0L;
        for(int idx = 0; idx < inventory.getSlots() && amount < maximum; idx++){
            int count = (int) Math.min(template.getMaxStackSize(), maximum - amount);
            ItemStack offered = template.copyWithCount(count);
            int remaining = inventory.insertItem(idx, offered, true).getCount();
            amount += Math.max(0, Math.min(count, count - remaining));
        }
        return amount;
    }

    // Merge eligible stacks without changing item components or reserved cargo
    public static void compact(IItemHandlerModifiable inventory, Predicate<ItemStack> eligible){
        if(inventory == null || eligible == null) return;
        for(int source = 1; source < inventory.getSlots(); source++){
            ItemStack stack = inventory.getStackInSlot(source);
            if(stack.isEmpty() || !eligible.test(stack)) continue;
            for(int target = 0; target < source && !stack.isEmpty(); target++){
                ItemStack existing = inventory.getStackInSlot(target);
                if(!existing.isEmpty() && (!eligible.test(existing)
                        || !ItemStack.isSameItemSameComponents(existing, stack))) continue;
                int space = Math.min(stack.getMaxStackSize(), inventory.getSlotLimit(target)) - existing.getCount();
                if(space <= 0) continue;
                int amount = Math.min(space, stack.getCount());
                amount -= inventory.insertItem(target, stack.copyWithCount(amount), true).getCount();
                if(amount <= 0) continue;
                inventory.setStackInSlot(target, stack.copyWithCount(existing.getCount() + amount));
                stack = stack.copyWithCount(stack.getCount() - amount);
                inventory.setStackInSlot(source, stack);
            }
        }
    }
}
