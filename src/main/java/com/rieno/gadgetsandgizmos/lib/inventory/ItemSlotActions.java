package com.rieno.gadgetsandgizmos.lib.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;
import java.util.function.UnaryOperator;

// Apply explicit item actions without consuming input when output space is unavailable
public final class ItemSlotActions{
    private ItemSlotActions(){}

    public static boolean apply(Container container, int inputSlot, int outputSlot,
            Predicate<ItemStack> allowed, UnaryOperator<ItemStack> action){
        if(inputSlot == outputSlot || !container.getItem(outputSlot).isEmpty()) return false;
        ItemStack input = container.getItem(inputSlot);
        if(input.isEmpty() || !allowed.test(input)) return false;
        ItemStack res = action.apply(input.copyWithCount(1));
        if(res == null || res.isEmpty() || res.getCount() != 1) return false;
        container.removeItem(inputSlot, 1);
        container.setItem(outputSlot, res);
        container.setChanged();
        return true;
    }
}
