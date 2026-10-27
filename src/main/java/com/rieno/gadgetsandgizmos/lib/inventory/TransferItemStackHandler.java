package com.rieno.gadgetsandgizmos.lib.inventory;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.HashMap;
import java.util.Map;

// Track direct slot edits as well as ordinary item handler insertion and extraction
public class TransferItemStackHandler extends ItemStackHandler implements TransferTrackedStorage{
    private final TransferMeter meter;
    private final Map<Integer, ItemStack> prev = new HashMap<>();

    public TransferItemStackHandler(int slots, TransferMeter meter){ super(slots); this.meter = meter; }

    @Override public TransferMeter.Rates transferRates(){ return meter.rates(); }

    @Override protected void onContentsChanged(int slot){
        ItemStack before = prev.getOrDefault(slot, ItemStack.EMPTY), after = getStackInSlot(slot);
        boolean same = ItemStack.isSameItemSameComponents(before, after);
        meter.record(same ? Math.max(0, after.getCount() - before.getCount()) : after.getCount(),
                same ? Math.max(0, before.getCount() - after.getCount()) : before.getCount());
        if(after.isEmpty()) prev.remove(slot);
        else prev.put(slot, after.copy());
    }

    // Loading saved contents establishes a baseline rather than an incoming transfer
    @Override protected void onLoad(){
        prev.clear();
        for(int slot = 0; slot < getSlots(); slot++){
            var stack = getStackInSlot(slot);
            if(!stack.isEmpty()) prev.put(slot, stack.copy());
        }
    }
}
