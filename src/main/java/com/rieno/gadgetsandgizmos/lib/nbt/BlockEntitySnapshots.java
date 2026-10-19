package com.rieno.gadgetsandgizmos.lib.nbt;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

// Recreate detached block-entity snapshots only when their registered type supports the backing state
public final class BlockEntitySnapshots{
    private BlockEntitySnapshots(){}

    // Invalid state/type pairs remain unavailable without entering the logging loadStatic failure path
    public static @Nullable BlockEntity create(Level level, BlockPos pos, BlockState state, CompoundTag data){
        if(state.isAir() || !state.hasBlockEntity()) return null;
        ResourceLocation id = ResourceLocation.tryParse(data.getString("id"));
        var type = id == null ? null : BuiltInRegistries.BLOCK_ENTITY_TYPE.get(id);
        if(type == null || !type.isValid(state)) return null;
        BlockEntity res = BlockEntity.loadStatic(pos, state, data.copy(), level.registryAccess());
        if(res == null) return null;
        res.setLevel(level);
        res.clearRemoved();
        return res;
    }
}
