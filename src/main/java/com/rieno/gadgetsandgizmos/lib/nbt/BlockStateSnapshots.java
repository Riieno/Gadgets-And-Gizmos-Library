package com.rieno.gadgetsandgizmos.lib.nbt;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

// Read portable block states without silently replacing unavailable registrations with air
public final class BlockStateSnapshots{
    private BlockStateSnapshots(){}

    // Apply an explicitly compatible fallback only when the original registration is unavailable
    public static @Nullable BlockState read(HolderGetter<Block> registry, CompoundTag data,
            Map<ResourceLocation, ResourceLocation> fallbacks){
        ResourceLocation id = ResourceLocation.tryParse(data.getString("Name"));
        if(id == null) return null;
        CompoundTag resolved = data;
        if(registry.get(ResourceKey.create(Registries.BLOCK, id)).isEmpty()){
            ResourceLocation replacement = fallbacks == null ? null : fallbacks.get(id);
            if(replacement == null || registry.get(ResourceKey.create(Registries.BLOCK, replacement)).isEmpty()) return null;
            resolved = data.copy();
            resolved.putString("Name", replacement.toString());
        }
        return NbtUtils.readBlockState(registry, resolved);
    }
}
