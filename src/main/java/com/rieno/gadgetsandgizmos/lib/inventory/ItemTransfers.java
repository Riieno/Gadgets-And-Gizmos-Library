package com.rieno.gadgetsandgizmos.lib.inventory;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.function.Predicate;
import java.util.function.Consumer;

// Transfer real items only after checking the destination's available space
public final class ItemTransfers{
    private ItemTransfers(){}

    // Move a bounded number of matching items and return any rejected remainder
    public static int move(IItemHandler source, IItemHandler target, Predicate<ItemStack> filter, int limit){
        return move(source, target, filter, limit, stack -> { throw new TransferRecoveryException(stack); });
    }

    // Recover items if a destination changes its acceptance after simulation
    public static int move(IItemHandler source, IItemHandler target, Predicate<ItemStack> filter, int limit, Consumer<ItemStack> recovery){
        if(source == null || target == null || ContainerAccessRegistry.sameStorage(source, target) || limit <= 0) return 0;
        int moved = 0;
        for(int idx = 0; idx < source.getSlots() && moved < limit; idx++){
            ItemStack offered = source.extractItem(idx, limit - moved, true);
            if(offered.isEmpty() || !filter.test(offered)) continue;
            int accepted = offered.getCount() - ItemHandlerHelper.insertItemStacked(target, offered, true).getCount();
            if(accepted <= 0) continue;
            ItemStack extracted = source.extractItem(idx, accepted, false);
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(target, extracted, false);
            moved += extracted.getCount() - remainder.getCount();
            if(!remainder.isEmpty()){
                remainder = source.insertItem(idx, remainder, false);
                if(!remainder.isEmpty()) remainder = ItemHandlerHelper.insertItemStacked(source, remainder, false);
                if(!remainder.isEmpty()) recovery.accept(remainder.copy());
            }
        }
        return moved;
    }

    public static final class TransferRecoveryException extends IllegalStateException{
        private final ItemStack remainder;
        private TransferRecoveryException(ItemStack stack){
            super("Storage rejected its own transferred items; recover the remainder");
            remainder = stack.copy();
        }
        public ItemStack remainder(){ return remainder.copy(); }
    }

    // Count exact item components in one inventory
    public static long count(IItemHandler handler, ItemStack item){
        long amount = 0;
        if(handler == null || item == null || item.isEmpty()) return amount;
        for(int idx = 0; idx < handler.getSlots(); idx++){
            ItemStack stack = handler.getStackInSlot(idx);
            if(ItemStack.isSameItemSameComponents(stack, item)) amount += stack.getCount();
        }
        return amount;
    }
}
