package com.rieno.gadgetsandgizmos.lib.probe;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

// Read terrain without requesting chunks or retaining stale support results
public final class LoadedTerrainAccess{
    private Level level;
    private long tick = Long.MIN_VALUE;
    private final Map<Long, LevelChunk> chunks = new HashMap<>();
    private final Map<SupportColumn, Boolean> support = new HashMap<>();

    // Clear cached terrain when the owning level advances
    public void beginTick(Level level){
        if(this.level == level && tick == level.getGameTime()) return;
        this.level = level;
        tick = level.getGameTime();
        chunks.clear();
        support.clear();
    }

    // Return only an immediately available block state
    public @Nullable BlockState blockState(BlockPos pos){
        if(level == null || level.isOutsideBuildHeight(pos)) return null;
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        long key = (long) chunkX << 32 | chunkZ & 0xffffffffL;
        if(!chunks.containsKey(key)){
            chunks.put(key, level.getChunkSource().getChunkNow(chunkX, chunkZ));
        }
        LevelChunk chunk = chunks.get(key);
        return chunk == null ? null : chunk.getBlockState(pos);
    }

    // Check a collidable or fluid surface inside the suspension envelope
    public boolean hasSupportColumn(double x, double z, double bottom,
                                    double maximumRise, double maximumDrop){
        if(level == null || !Double.isFinite(x) || !Double.isFinite(z)
                || !Double.isFinite(bottom) || !Double.isFinite(maximumRise)
                || !Double.isFinite(maximumDrop)) return false;
        SupportColumn key = new SupportColumn(x, z, bottom, maximumRise, maximumDrop);
        Boolean cached = support.get(key);
        if(cached != null) return cached;
        boolean res = resolveSupport(key);
        support.put(key, res);
        return res;
    }

    // Inspect one loaded vertical column
    private boolean resolveSupport(SupportColumn column){
        int blockX = (int) Math.floor(column.x());
        int blockZ = (int) Math.floor(column.z());
        int topY = Math.min(level.getMaxBuildHeight() - 1,
                (int) Math.floor(column.bottom() + Math.max(0.0D, column.rise())));
        int bottomY = Math.max(level.getMinBuildHeight(),
                (int) Math.floor(column.bottom() - Math.max(0.0D, column.drop())));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for(int y = topY; y >= bottomY; y--){
            pos.set(blockX, y, blockZ);
            BlockState state = blockState(pos);
            if(state == null) return false;
            if(!state.getFluidState().isEmpty()) return true;
            if(state.isAir()) continue;
            for(AABB bounds : state.getCollisionShape(level, pos, CollisionContext.empty()).toAabbs()){
                double surface = y + bounds.maxY;
                if(column.x() >= blockX + bounds.minX - 1.0E-6D
                        && column.x() <= blockX + bounds.maxX + 1.0E-6D
                        && column.z() >= blockZ + bounds.minZ - 1.0E-6D
                        && column.z() <= blockZ + bounds.maxZ + 1.0E-6D
                        && surface <= column.bottom() + column.rise()
                        && surface >= column.bottom() - column.drop()) return true;
            }
        }
        return false;
    }

    private record SupportColumn(double x, double z, double bottom, double rise, double drop){}
}
