package com.rieno.gadgetsandgizmos.lib.color;

import com.rieno.gadgetsandgizmos.lib.network.BlockEntityDataSync;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

// Retain an optional visual preset separately from a block entity's native settings
public final class ColorEffectState{
    public static final String NBT_KEY = "GadgetsNGizmosColorEffect";
    private ColorEffectPreset preset = ColorEffectPreset.DEFAULT;

    public ColorEffectPreset preset(){ return preset; }

    // Apply changes on the owning game thread and use the owner's normal update packet
    public void apply(BlockEntity owner, ColorEffectPreset val){
        if(owner.getLevel() == null || owner.getLevel().isClientSide || preset.equals(val)) return;
        preset = val;
        owner.setChanged();
        BlockEntityDataSync.enqueue(owner);
    }

    public void read(CompoundTag tag){ preset = ColorEffectPreset.read(tag.getCompound(NBT_KEY)); }

    public void write(CompoundTag tag){
        if(preset.active() || preset.additive()) tag.put(NBT_KEY, preset.write());
        else tag.remove(NBT_KEY);
    }
}
