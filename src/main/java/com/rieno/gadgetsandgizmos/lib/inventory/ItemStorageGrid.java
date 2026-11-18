package com.rieno.gadgetsandgizmos.lib.inventory;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// A filtered view of real capability slots; displayed stacks are never writable inventory copies
public final class ItemStorageGrid{
    private record Entry(IItemHandler handler, int slot){}
    private final List<IItemHandler> handlers = new ArrayList<>();
    private final List<Entry> entries = new ArrayList<>();
    private final List<Group> groups = new ArrayList<>();
    private boolean grouped;
    private int offset;
    private int columns = 9;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public void refresh(Collection<? extends IItemHandler> sources, String search, int row, int rows){
        refresh(sources, search, row, rows, 9);
    }

    // Page the same live slots through a host's wider inventory grid
    public void refresh(Collection<? extends IItemHandler> sources, String search, int row, int rows, int columns){
        if(columns < 1 || rows < 1) throw new IllegalArgumentException("Inventory grid dimensions must be positive");
        grouped = false;
        groups.clear();
        this.columns = columns;
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
        offset = Math.max(0, Math.min(row, Math.max(0, rows() - rows))) * columns;
    }

    // Group matching components into one virtual slot while retaining every physical extraction source
    public void refreshGrouped(Collection<? extends IItemHandler> sources, String search, int row, int rows, int columns){
        if(columns < 1 || rows < 1) throw new IllegalArgumentException("Inventory grid dimensions must be positive");
        this.columns = columns;
        grouped = true;
        handlers.clear();
        entries.clear();
        groups.clear();
        var indexed = new LinkedHashMap<ItemStackKey, Group>();
        Set<IItemHandler> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        String filter = search.strip().toLowerCase(Locale.ROOT);
        for(IItemHandler handler : sources){
            if(handler == null || !seen.add(handler)) continue;
            handlers.add(handler);
            for(int slot = 0; slot < handler.getSlots(); slot++){
                ItemStack stack = handler.getStackInSlot(slot);
                if(stack.isEmpty()) continue;
                var key = new ItemStackKey(stack);
                Group group = indexed.get(key);
                if(group == null){
                    group = new Group(key.stack());
                    indexed.put(key, group);
                }
                group.count += Math.min(Long.MAX_VALUE - group.count, stack.getCount());
                group.entries.add(new Entry(handler, slot));
            }
        }
        for(Group group : indexed.values()){
            if(filter.isEmpty() || group.sample.getHoverName().getString().toLowerCase(Locale.ROOT).contains(filter)
                    || BuiltInRegistries.ITEM.getKey(group.sample.getItem()).toString().contains(filter)) groups.add(group);
        }
        offset = Math.max(0, Math.min(row, Math.max(0, rows() - rows))) * columns;
    }

    public int rows(){ return ((grouped ? groups.size() : entries.size()) + columns - 1) / columns; }
    public int row(){ return offset / columns; }

    public ItemStack stack(int slot){
        if(grouped){
            Group group = group(slot);
            return group == null ? ItemStack.EMPTY : group.sample.copyWithCount((int)Math.min(group.count, group.sample.getMaxStackSize()));
        }
        Entry entry = entry(slot);
        return entry == null ? ItemStack.EMPTY : entry.handler.getStackInSlot(entry.slot).copy();
    }

    // Read the full displayed total without creating an oversized ItemStack
    public long count(int slot){
        if(!grouped) return stack(slot).getCount();
        Group group = group(slot);
        return group == null ? 0 : group.count;
    }

    public ItemStack extract(int slot, int amount){
        if(grouped) return extractGroup(slot, amount, false);
        Entry entry = entry(slot);
        return entry == null ? ItemStack.EMPTY : entry.handler.extractItem(entry.slot, amount, false);
    }

    public ItemStack simulateExtract(int slot, int amount){
        if(grouped) return extractGroup(slot, amount, true);
        Entry entry = entry(slot);
        return entry == null ? ItemStack.EMPTY : entry.handler.extractItem(entry.slot, amount, true);
    }

    // Exchange a whole slot only when both capability operations can complete; recover any rejected items
    public ItemStack exchange(int slot, ItemStack offered, java.util.function.Consumer<ItemStack> recover){
        if(grouped) return insert(offered, slot, false);
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

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private Entry entry(int slot){
        if(grouped){
            Group group = group(slot);
            return group == null || group.entries.isEmpty() ? null : group.entries.getFirst();
        }
        int idx = offset + slot;
        return slot < 0 || idx < 0 || idx >= entries.size() ? null : entries.get(idx);
    }

    private Group group(int slot){
        int idx = offset + slot;
        return slot < 0 || idx < 0 || idx >= groups.size() ? null : groups.get(idx);
    }

    // Recheck each live source so stale slots cannot extract another item variant
    private ItemStack extractGroup(int slot, int amount, boolean simulate){
        Group group = group(slot);
        if(group == null || amount <= 0) return ItemStack.EMPTY;
        int limit = Math.min(amount, group.sample.getMaxStackSize());
        ItemStack res = ItemStack.EMPTY;
        for(Entry entry : group.entries){
            if(res.getCount() >= limit) break;
            ItemStack stored = entry.handler.getStackInSlot(entry.slot);
            if(!ItemStack.isSameItemSameComponents(group.sample, stored)) continue;
            ItemStack available = entry.handler.extractItem(entry.slot, limit - res.getCount(), true);
            if(!ItemStack.isSameItemSameComponents(group.sample, available)) continue;
            ItemStack extracted = simulate ? available : entry.handler.extractItem(entry.slot, Math.min(limit - res.getCount(), available.getCount()), false);
            if(res.isEmpty()) res = extracted.copy();
            else res.grow(extracted.getCount());
        }
        return res;
    }

    private static final class Group{
        private final ItemStack sample;
        private final List<Entry> entries = new ArrayList<>();
        private long count;

        private Group(ItemStack sample){ this.sample = sample; }
    }
}
