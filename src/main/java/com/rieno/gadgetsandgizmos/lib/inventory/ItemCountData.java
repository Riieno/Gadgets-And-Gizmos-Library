package com.rieno.gadgetsandgizmos.lib.inventory;

import net.minecraft.world.inventory.ContainerData;

// Synchronize nonnegative item totals through vanilla menu data's sixteen-bit values
public final class ItemCountData implements ContainerData{
    private final long[] counts;

    public ItemCountData(int slots){ counts = new long[slots]; }

    public long count(int slot){ return counts[slot]; }
    public void count(int slot, long amount){ counts[slot] = Math.max(0, amount); }

    @Override public int getCount(){ return counts.length * 4; }
    @Override public int get(int idx){ return (int)(counts[idx / 4] >>> ((idx % 4) * 16)) & 0xFFFF; }
    @Override public void set(int idx, int val){
        int shift = (idx % 4) * 16;
        counts[idx / 4] = (counts[idx / 4] & ~(0xFFFFL << shift)) | ((val & 0xFFFFL) << shift);
    }
}
