package com.rieno.gadgetsandgizmos.lib.inventory;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// A filtered view of real capability slots; displayed stacks are never writable inventory copies
public final class ItemStorageGrid{
    private record Entry(IItemHandler handler, int slot){}
    private final List<IItemHandler> handlers = new ArrayList<>();
    private final List<Entry> entries = new ArrayList<>();
    private int offset;

    public void refresh(Collection<? extends IItemHandler> sources, String search, int row, int rows){
        handlers.clear();
        entries.clear();
        Set<IItemHandler> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        String filter = search.strip().toLowerCase(Locale.ROOT);
        for(IItemHandler handler : sources){
            if(handler == null || !seen.add(handler)) continue;
            handlers.add(handler);
            for(int slot = 0; slot < handler.getSlots(); slot++){
                ItemStack stack = handler.getStackInSlot(slot);
                if(!filter.isEmpty() && (stack.isEmpty() || !(stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(filter)
                        || BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().contains(filter)))) continue;
                entries.add(new Entry(handler, slot));
            }
        }
        offset = Math.max(0, Math.min(row, Math.max(0, (entries.size() + 8) / 9 - rows))) * 9;
    }

    public int rows(){ return (entries.size() + 8) / 9; }
    public int row(){ return offset / 9; }

    public ItemStack stack(int slot){
        Entry entry = entry(slot);
        return entry == null ? ItemStack.EMPTY : entry.handler.getStackInSlot(entry.slot).copy();
    }

    public ItemStack extract(int slot, int amount){
        Entry entry = entry(slot);
        return entry == null ? ItemStack.EMPTY : entry.handler.extractItem(entry.slot, amount, false);
    }

    public ItemStack simulateExtract(int slot, int amount){
        Entry entry = entry(slot);
        return entry == null ? ItemStack.EMPTY : entry.handler.extractItem(entry.slot, amount, true);
    }

    // Exchange a whole slot only when both capability operations can complete; recover any rejected items
    public ItemStack exchange(int slot, ItemStack offered, java.util.function.Consumer<ItemStack> recover){
        Entry entry = entry(slot);
        if(entry == null || offered.isEmpty()) return offered;
        ItemStack stored = entry.handler.getStackInSlot(entry.slot).copy();
        if(stored.isEmpty() || stored.getCount() > stored.getMaxStackSize()
                || !entry.handler.isItemValid(entry.slot, offered)
                || offered.getCount() > Math.min(entry.handler.getSlotLimit(entry.slot), offered.getMaxStackSize())) return offered;
        ItemStack preview = entry.handler.extractItem(entry.slot, stored.getCount(), true);
        if(preview.getCount() != stored.getCount() || !ItemStack.isSameItemSameComponents(preview, stored)) return offered;
        ItemStack extracted = entry.handler.extractItem(entry.slot, stored.getCount(), false);
        if(extracted.getCount() != stored.getCount()){
            recover.accept(entry.handler.insertItem(entry.slot, extracted, false));
            return offered;
        }
        ItemStack remainder = entry.handler.insertItem(entry.slot, offered, false);
        if(remainder.isEmpty()) return extracted;
        recover.accept(insert(extracted, slot, false));
        return remainder;
    }

    // Prefer the clicked slot, then merge into the network before filling empty slots
    public ItemStack insert(ItemStack stack, int preferredSlot, boolean simulate){
        ItemStack remaining = stack.copy();
        Entry preferred = entry(preferredSlot);
        if(preferred != null) remaining = preferred.handler.insertItem(preferred.slot, remaining, simulate);
        for(int pass = 0; pass < 2 && !remaining.isEmpty(); pass++){
            for(IItemHandler handler : handlers){
                for(int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++){
                    if(preferred != null && preferred.handler == handler && preferred.slot == slot) continue;
                    ItemStack stored = handler.getStackInSlot(slot);
                    if((pass == 0) != !stored.isEmpty()) continue;
                    remaining = handler.insertItem(slot, remaining, simulate);
                }
            }
        }
        return remaining;
    }

    private Entry entry(int slot){
        int idx = offset + slot;
        return slot < 0 || idx < 0 || idx >= entries.size() ? null : entries.get(idx);
    }
}
