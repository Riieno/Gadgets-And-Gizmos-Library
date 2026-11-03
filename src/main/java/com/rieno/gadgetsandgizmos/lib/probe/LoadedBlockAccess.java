package com.rieno.gadgetsandgizmos.lib.probe;

import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelActorLookup;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

// Inspect one explicit scope without requesting chunks, waiting for futures or creating block entities
public final class LoadedBlockAccess{
    private LoadedBlockAccess(){}

    // Null means unavailable; an available air block remains a normal block state
    public static @Nullable BlockState blockState(@Nullable Level level, @Nullable UUID subLevelId, @Nullable BlockPos pos){
        LevelChunk chunk = chunk(level, subLevelId, pos);
        return chunk == null ? null : chunk.getBlockState(pos);
    }

    public static @Nullable BlockEntity blockEntity(@Nullable Level level, @Nullable UUID subLevelId, @Nullable BlockPos pos){
        LevelChunk chunk = chunk(level, subLevelId, pos);
        if(chunk == null) return null;
        BlockEntity entity = chunk.getBlockEntities().get(pos);
        if(entity != null && !entity.isRemoved()) return entity;
        if(subLevelId == null) return null;
        SubLevel body = SableLevelApi.subLevel(level, subLevelId);
        if(body == null || body.isRemoved()) return null;
        var plot = body.getPlot();
        if(plot instanceof SubLevelActorLookup lookup){
            entity = lookup.gadgetsngizmos$actorBlockEntity(pos);
            return entity == null || entity.isRemoved() || !pos.equals(entity.getBlockPos()) ? null : entity;
        }
        for(Object actor : plot.getBlockEntityActors()){
            if(actor instanceof BlockEntity candidate && !candidate.isRemoved() && pos.equals(candidate.getBlockPos())) return candidate;
        }
        return null;
    }

    public static <T extends BlockEntity> @Nullable T blockEntity(Level level, @Nullable UUID subLevelId,
                                                                  BlockPos pos, Class<T> type){
        BlockEntity entity = blockEntity(level, subLevelId, pos);
        return type.isInstance(entity) ? type.cast(entity) : null;
    }

    private static @Nullable LevelChunk chunk(@Nullable Level level, @Nullable UUID subLevelId, @Nullable BlockPos pos){
        if(level == null || pos == null) return null;
        if(subLevelId == null){
            if(level.isOutsideBuildHeight(pos) || SubLevelBlockEntityCollector.isSubLevelPlotPosition(level, pos)) return null;
            return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        }
        SubLevel body = SableLevelApi.subLevel(level, subLevelId);
        if(body == null || body.isRemoved()) return null;
        var plot = body.getPlot();
        var holder = plot.getChunkHolder(plot.toLocal(new ChunkPos(pos)));
        return holder == null ? null : holder.getChunk();
    }
}
