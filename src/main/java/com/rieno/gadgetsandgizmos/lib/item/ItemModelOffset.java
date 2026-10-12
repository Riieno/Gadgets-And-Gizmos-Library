package com.rieno.gadgetsandgizmos.lib.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

// Store a bounded model adjustment without replacing other item data
public final class ItemModelOffset{
    private final String key;
    private final int minSteps;
    private final int maxSteps;
    private final float pixelsPerStep;

    // Configure a saved adjustment whose default is zero
    public ItemModelOffset(String key, int minSteps, int maxSteps, float pixelsPerStep){
        if(key == null || key.isBlank()) throw new IllegalArgumentException("Model offset key is required");
        if(minSteps > 0 || maxSteps < 0) throw new IllegalArgumentException("Model offset range must include zero");
        if(!Float.isFinite(pixelsPerStep) || pixelsPerStep <= 0) throw new IllegalArgumentException("Model offset step must be positive and finite");
        this.key = key;
        this.minSteps = minSteps;
        this.maxSteps = maxSteps;
        this.pixelsPerStep = pixelsPerStep;
    }

    // Read a bounded adjustment from the item's custom NBT
    public int readSteps(ItemStack stack){
        if(stack == null || stack.isEmpty()) return 0;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return clampSteps(tag.getInt(key));
    }

    // Save an adjustment and retain every unrelated NBT field
    public boolean writeSteps(ItemStack stack, int steps){
        if(stack == null || stack.isEmpty()) return false;
        int val = clampSteps(steps);
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if(val == 0){
            if(!tag.contains(key)) return false;
            tag.remove(key);
        }else{
            if(tag.contains(key, Tag.TAG_INT) && tag.getInt(key) == val) return false;
            tag.putInt(key, val);
        }
        if(tag.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
        else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return true;
    }

    // Advance a saved adjustment without overflowing or exceeding its limits
    public boolean adjustSteps(ItemStack stack, int delta){
        long val = (long)readSteps(stack) + delta;
        return writeSteps(stack, (int)Math.max(minSteps, Math.min(maxSteps, val)));
    }

    // Return the saved adjustment in model pixels
    public float offsetPixels(ItemStack stack){
        return readSteps(stack) * pixelsPerStep;
    }

    // Keep saved or requested step counts within their configured range
    private int clampSteps(int steps){
        return Math.max(minSteps, Math.min(maxSteps, steps));
    }
}
