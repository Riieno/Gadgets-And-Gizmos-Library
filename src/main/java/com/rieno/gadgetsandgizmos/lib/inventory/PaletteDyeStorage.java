package com.rieno.gadgetsandgizmos.lib.inventory;

import com.rieno.gadgetsandgizmos.lib.color.WeightedColorGradient;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;

// Store large dye counts without exceeding vanilla item-stack serialization or cursor limits
public final class PaletteDyeStorage{
    public static final int CAPACITY = 128;
    public static final int GLOW_SLOT = 16;
    public static final int SLOTS = 17;
    private int glowInkSacs;
    private final int[] counts = new int[16];

    public int count(int color){ return color == GLOW_SLOT ? glowInkSacs : color >= 0 && color < 16 ? counts[color] : 0; }

    // Represent stored dyes as proportional color bands; glow ink does not add a dye color
    public WeightedColorGradient gradient(){
        var colors = new ArrayList<WeightedColorGradient.Color>();
        for(int idx = 0; idx < counts.length; idx++){
            if(counts[idx] > 0) colors.add(new WeightedColorGradient.Color(DyeColor.byId(idx).getTextureDiffuseColor(), counts[idx]));
        }
        return new WeightedColorGradient(colors);
    }

    public boolean loaded(){
        for(int count : counts) if(count < 1) return false;
        return true;
    }

    // Return the ingredient assigned to each fixed palette slot
    public static Item slotItem(int slot){
        if(slot == GLOW_SLOT) return Items.GLOW_INK_SAC;
        if(slot < 0 || slot >= 16) return Items.AIR;
        return DyeItem.byColor(DyeColor.byId(slot));
    }

    public ItemStack insert(int color, ItemStack offered){
        if(color == GLOW_SLOT){
            if(!offered.is(Items.GLOW_INK_SAC)) return offered;
            int added = Math.min(offered.getCount(), CAPACITY - glowInkSacs);
            glowInkSacs += added;
            return offered.copyWithCount(offered.getCount() - added);
        }
        if(color < 0 || color >= 16 || !(offered.getItem() instanceof DyeItem dye) || dye.getDyeColor().getId() != color) return offered;
        int added = Math.min(offered.getCount(), CAPACITY - counts[color]);
        counts[color] += added;
        return offered.copyWithCount(offered.getCount() - added);
    }

    public ItemStack extract(int color, int maximum){
        if(color < 0 || color >= SLOTS || maximum <= 0) return ItemStack.EMPTY;
        if(color == GLOW_SLOT){
            int amount = Math.min(Math.min(64, maximum), glowInkSacs);
            glowInkSacs -= amount;
            return amount == 0 ? ItemStack.EMPTY : new ItemStack(Items.GLOW_INK_SAC, amount);
        }
        int amount = Math.min(Math.min(64, maximum), counts[color]);
        counts[color] -= amount;
        return amount == 0 ? ItemStack.EMPTY : new ItemStack(DyeItem.byColor(DyeColor.byId(color)), amount);
    }

    // Keep one dye of each color loaded after paying for a non-default preset
    public boolean canPay(int[] cost){
        if(cost.length != 16) return false;
        for(int idx = 0; idx < 16; idx++) if(cost[idx] < 0 || counts[idx] < cost[idx] + 1) return false;
        return true;
    }

    public boolean pay(int[] cost){
        if(!canPay(cost)) return false;
        for(int idx = 0; idx < 16; idx++) counts[idx] -= cost[idx];
        return true;
    }

    // Pay dye and glow ingredients together without partial deductions
    public boolean canPay(int[] cost, int glowCost){
        return glowCost >= 0 && glowInkSacs >= glowCost && canPay(cost);
    }

    public boolean pay(int[] cost, int glowCost){
        if(!canPay(cost, glowCost)) return false;
        pay(cost);
        glowInkSacs -= glowCost;
        return true;
    }

    public void read(CompoundTag tag){
        glowInkSacs = Math.clamp(tag.getInt("GlowInkSacs"), 0, CAPACITY);
        var values = tag.getIntArray("Dyes");
        for(int idx = 0; idx < 16; idx++) counts[idx] = idx < values.length ? Math.clamp(values[idx], 0, CAPACITY) : 0;
    }

    public CompoundTag write(){
        var tag = new CompoundTag();
        tag.putIntArray("Dyes", counts.clone());
        tag.putInt("GlowInkSacs", glowInkSacs);
        return tag;
    }
}
