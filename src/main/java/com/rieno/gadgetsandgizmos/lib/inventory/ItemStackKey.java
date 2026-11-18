package com.rieno.gadgetsandgizmos.lib.inventory;

import net.minecraft.world.item.ItemStack;

// Compare item components without retaining mutable inventory stacks
record ItemStackKey(ItemStack stack){
    ItemStackKey{ stack = stack.copyWithCount(1); }

    @Override public boolean equals(Object other){
        return other instanceof ItemStackKey key && ItemStack.isSameItemSameComponents(stack, key.stack);
    }

    @Override public int hashCode(){ return ItemStack.hashItemAndComponents(stack); }
}
