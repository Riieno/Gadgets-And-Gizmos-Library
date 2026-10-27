package com.rieno.gadgetsandgizmos.lib.discovery;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

// Discover mounted bodies once without loading missing bodies or unrelated chunks
public final class MountedBlockEntities{
    private MountedBlockEntities(){}

    // Fallback positions describe unassembled heads only
    public static List<BlockEntity> collect(Level level, Collection<UUID> bodyIds, Collection<BlockPos> fallbackPositions){
        if(level == null) return List.of();
        var found = new LinkedHashMap<BlockPos, BlockEntity>();
        for(UUID id : new LinkedHashSet<>(bodyIds)){
            if(id == null) continue;
            Object body = SubLevelBlockEntityCollector.getSubLevel(level, id);
            for(BlockEntity be : SubLevelBlockEntityCollector.getBlockEntities(body)){
                if(!be.isRemoved()) found.putIfAbsent(be.getBlockPos(), be);
            }
        }
        for(BlockPos pos : fallbackPositions){
            if(pos == null || !level.isLoaded(pos)) continue;
            BlockEntity be = level.getBlockEntity(pos);
            if(be != null && !be.isRemoved()) found.putIfAbsent(pos, be);
        }
        return new ArrayList<>(found.values());
    }
}
