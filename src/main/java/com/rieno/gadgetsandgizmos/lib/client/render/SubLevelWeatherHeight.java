package com.rieno.gadgetsandgizmos.lib.client.render;

import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;

// Sample rain shelter within loaded plots and a fixed vertical budget
public final class SubLevelWeatherHeight{
    private static final int MAX_VERTICAL_SAMPLES = 4096;

    private SubLevelWeatherHeight(){}

    // Find the highest solid or fluid surface without scanning empty or invalid body bounds
    public static int rainHeight(Level level, int x, int offset, int z, Iterable<? extends SubLevel> bodies){
        int height = Integer.MIN_VALUE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Vector3d probe = new Vector3d();
        for(SubLevel body : bodies){
            if(body == null || body.isRemoved() || body.getPlot() == null) continue;
            var local = body.getPlot().getBoundingBox();
            if(local.minX() > local.maxX() || local.minY() > local.maxY() || local.minZ() > local.maxZ()) continue;
            var bounds = body.boundingBox();
            if(!Double.isFinite(bounds.minX()) || !Double.isFinite(bounds.maxX())
                    || !Double.isFinite(bounds.minY()) || !Double.isFinite(bounds.maxY())
                    || !Double.isFinite(bounds.minZ()) || !Double.isFinite(bounds.maxZ())
                    || bounds.minX() > bounds.maxX() || bounds.minY() >= bounds.maxY()
                    || bounds.minZ() > bounds.maxZ() || x + 0.5 < bounds.minX() || x + 0.5 > bounds.maxX()
                    || z + 0.5 < bounds.minZ() || z + 0.5 > bounds.maxZ()) continue;
            long top = (long) Math.min(level.getMaxBuildHeight(), Math.ceil(bounds.maxY()));
            long bottom = (long) Math.max(level.getMinBuildHeight(), Math.floor(bounds.minY()));
            bottom = Math.max(bottom, top - MAX_VERTICAL_SAMPLES);
            var pose = body.logicalPose();
            for(long y = top - 1; y >= bottom; y--){
                pose.transformPositionInverse(probe.set(x + 0.5, y, z + 0.5));
                if(!Double.isFinite(probe.x) || !Double.isFinite(probe.y) || !Double.isFinite(probe.z)) break;
                if(probe.x < local.minX() || probe.x >= (double) local.maxX() + 1
                        || probe.y < local.minY() || probe.y >= (double) local.maxY() + 1
                        || probe.z < local.minZ() || probe.z >= (double) local.maxZ() + 1
                        || probe.y < level.getMinBuildHeight() || probe.y >= level.getMaxBuildHeight()) continue;
                pos.set(probe.x, probe.y, probe.z);
                if(!level.hasChunkAt(pos)) continue;
                var state = level.getBlockState(pos);
                if(!state.blocksMotion() && state.getFluidState().isEmpty()) continue;
                height = Math.max(height, (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, y + (long) offset)));
                break;
            }
        }
        return height;
    }
}
