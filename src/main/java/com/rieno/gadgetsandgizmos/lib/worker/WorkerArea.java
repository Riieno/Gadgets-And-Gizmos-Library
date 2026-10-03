package com.rieno.gadgetsandgizmos.lib.worker;

import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Bound a worker discovery area to loaded blocks without forcing chunks to load
public record WorkerArea(BlockPos min, BlockPos max){
    public static final long MAX_VOLUME = 65536L;
    public static final int MAX_SIDE = 256;

    public WorkerArea{
        if(min == null || max == null) throw new IllegalArgumentException("An area needs two corners");
        BlockPos first = min;
        BlockPos second = max;
        min = new BlockPos(Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ()));
        max = new BlockPos(Math.max(first.getX(), second.getX()), Math.max(first.getY(), second.getY()),
                Math.max(first.getZ(), second.getZ()));
        long width = (long)max.getX() - min.getX() + 1L;
        long height = (long)max.getY() - min.getY() + 1L;
        long depth = (long)max.getZ() - min.getZ() + 1L;
        if(width > MAX_SIDE || height > MAX_SIDE || depth > MAX_SIDE
                || width * height * depth > MAX_VOLUME) throw new IllegalArgumentException("Worker area is too large");
    }

    // Check whether a block is inside this inclusive area
    public boolean contains(BlockPos pos){
        return pos != null && pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    // Move one selected wall while retaining the opposite wall and the area limits
    public @Nullable WorkerArea moveFace(Direction face, int blocks){
        if(face == null || blocks == 0 || Math.abs((long)blocks) > MAX_SIDE) return null;
        int x0 = min.getX(), y0 = min.getY(), z0 = min.getZ();
        int x1 = max.getX(), y1 = max.getY(), z1 = max.getZ();
        switch(face){
            case WEST -> x0 += blocks;
            case EAST -> x1 += blocks;
            case DOWN -> y0 += blocks;
            case UP -> y1 += blocks;
            case NORTH -> z0 += blocks;
            case SOUTH -> z1 += blocks;
        }
        if(x0 > x1 || y0 > y1 || z0 > z1) return null;
        try{
            return new WorkerArea(new BlockPos(x0, y0, z0), new BlockPos(x1, y1, z1));
        }catch(IllegalArgumentException ignored){
            return null;
        }
    }

    // Discover non-air blocks from loaded chunks in world or sublevel plot space
    public List<BlockPos> loadedBlocks(Level level, @Nullable UUID subLevelId){
        return scan(level, subLevelId).blocks();
    }

    // Report whether the whole area was loaded so callers can avoid caching a partial result
    public Scan scan(Level level, @Nullable UUID subLevelId){
        if(level == null) return new Scan(List.of(), false);
        List<BlockPos> found = new ArrayList<>();
        Map<Long, Boolean> loadedChunks = new HashMap<>();
        boolean complete = true;
        for(BlockPos pos : BlockPos.betweenClosed(min, max)){
            long chunk = ((long)(pos.getX() >> 4) & 0xFFFFFFFFL)
                    | ((long)(pos.getZ() >> 4) << 32);
            boolean loaded = loadedChunks.computeIfAbsent(chunk,
                    ignored -> SubLevelBlockEntityCollector.isTargetLoaded(level, subLevelId, pos));
            if(!loaded){
                complete = false;
                continue;
            }
            if(level.getBlockState(pos).isAir()) continue;
            found.add(pos.immutable());
        }
        return new Scan(List.copyOf(found), complete);
    }

    public record Scan(List<BlockPos> blocks, boolean complete){}
}
