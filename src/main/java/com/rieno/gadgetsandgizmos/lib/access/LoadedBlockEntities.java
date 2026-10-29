package com.rieno.gadgetsandgizmos.lib.access;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jetbrains.annotations.Nullable;

// Inspect installed block entities without loading chunks or creating replacements
public final class LoadedBlockEntities{
    private LoadedBlockEntities(){}

    public static @Nullable BlockEntity get(Level level, BlockPos pos){
        if(level == null || pos == null || level.isOutsideBuildHeight(pos)) return null;
        var chunk = level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false);
        if(!(chunk instanceof LevelChunk loaded)) return null;
        var entity = loaded.getBlockEntities().get(pos);
        return entity == null || entity.isRemoved() ? null : entity;
    }
}
