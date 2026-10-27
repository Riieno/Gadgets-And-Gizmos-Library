package com.rieno.gadgetsandgizmos.lib.hologram;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.Tags;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

// Sample loaded root-world terrain and remove faces hidden below the exterior surface
public final class SurfaceHologramSampler{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private SurfaceHologramSampler(){}

    // Never load or generate chunks on behalf of a projection
    public static HologramChunk sample(ServerLevel level, ChunkPos pos, boolean obfuscateOres){
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
        if(chunk == null) return new HologramChunk(pos, 0x79A94E, 0x48A13B, 0x3F76E4, List.of());
        var biome = level.getBiome(new BlockPos(pos.getMiddleBlockX(), level.getSeaLevel(), pos.getMiddleBlockZ())).value();
        int minY = level.getMinBuildHeight();
        ExteriorHologramVolume volume = new ExteriorHologramVolume(level, chunk);
        List<HologramChunk.SurfaceBlock> blocks = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        scan:
        for(int x = 0; x < 16; x++) for(int z = 0; z < 16; z++){
            int surface = volume.surfaceHeight(x, z);
            for(int y = surface; y >= minY; y--){
                cursor.set(pos.getMinBlockX() + x, y, pos.getMinBlockZ() + z);
                BlockState state = volume.getBlockState(cursor);
                if(state.isAir() || state.getRenderShape() == net.minecraft.world.level.block.RenderShape.INVISIBLE && state.getFluidState().isEmpty()) continue;
                if(!volume.exposed(cursor)) continue;
                int faces = 0;
                for(Direction dir : Direction.values()){
                    BlockPos next = cursor.relative(dir);
                    if(next.getY() < minY || !volume.exposesFace(cursor, dir)) continue;
                    if(HologramFaceVisibility.shouldRenderFace(state, volume, cursor, dir, true)) faces |= 1 << dir.ordinal();
                }
                if(faces == 0) continue;
                blocks.add(new HologramChunk.SurfaceBlock(new BlockPos(x, y, z), maskOre(state, y, surface, obfuscateOres), faces));
                if(blocks.size() >= HologramChunk.MAX_BLOCKS) break scan;
            }
        }
        return snapshot(chunk, biome.getGrassColor(pos.getMiddleBlockX(), pos.getMiddleBlockZ()),
                biome.getFoliageColor(), biome.getWaterColor(), blocks);
    }

    // Average every loaded non-void surface column in the selected region
    public static double averageHeight(ServerLevel level, HologramRegion region, double fallback){
        long sum = 0;
        int count = 0;
        for(int x = region.minX(); x <= region.maxX(); x++) for(int z = region.minZ(); z <= region.maxZ(); z++){
            LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
            if(chunk == null) continue;
            for(int bx = 0; bx < 16; bx++) for(int bz = 0; bz < 16; bz++){
                int top = surfaceHeight(chunk, bx, bz, level.getMinBuildHeight());
                if(top < level.getMinBuildHeight()) continue;
                sum += top + 1;
                count++;
            }
        }
        return count == 0 ? fallback : sum / (double)count;
    }

    // Expose cut faces at the slice boundary while keeping true surface ore masking
    public static HologramChunk sample(ServerLevel level, ChunkPos pos, boolean obfuscateOres, HologramSlice slice){
        if(slice.surface()) return sample(level, pos, obfuscateOres);
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
        if(chunk == null) return new HologramChunk(pos, 0x79A94E, 0x48A13B, 0x3F76E4, List.of());
        var biome = level.getBiome(new BlockPos(pos.getMiddleBlockX(), level.getSeaLevel(), pos.getMiddleBlockZ())).value();
        int bottom = Math.max(slice.minY(), level.getMinBuildHeight());
        int top = Math.min(slice.maxY(), level.getMaxBuildHeight());
        List<HologramChunk.SurfaceBlock> blocks = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for(int x = 0; x < 16; x++) for(int z = 0; z < 16; z++){
            int surface = surfaceHeight(chunk, x, z, level.getMinBuildHeight());
            for(int y = top - 1; y >= bottom; y--){
                cursor.set(pos.getMinBlockX() + x, y, pos.getMinBlockZ() + z);
                BlockState state = chunk.getBlockState(cursor);
                if(state.isAir()) continue;
                int faces = 0;
                for(Direction dir : Direction.values()){
                    BlockPos next = cursor.relative(dir);
                    LevelChunk neighbor = level.getChunkSource().getChunkNow(next.getX() >> 4, next.getZ() >> 4);
                    boolean included = slice.contains(next.getY()) && neighbor != null;
                    if(HologramFaceVisibility.shouldRenderFace(state, level, cursor, dir, included)) faces |= 1 << dir.ordinal();
                }
                if(faces != 0) blocks.add(new HologramChunk.SurfaceBlock(new BlockPos(x, y, z), maskOre(state, y, surface, obfuscateOres), faces));
            }
        }
        return snapshot(chunk, biome.getGrassColor(pos.getMiddleBlockX(), pos.getMiddleBlockZ()), biome.getFoliageColor(), biome.getWaterColor(), blocks);
    }

    // Send the public rotational speed without sending inventories or block entity NBT
    private static HologramChunk snapshot(LevelChunk chunk, int grass, int foliage, int water, List<HologramChunk.SurfaceBlock> blocks){
        Map<BlockPos, Float> speeds = new LinkedHashMap<>();
        for(var block : blocks){
            if(!block.state().hasBlockEntity() || speeds.size() >= 4096) continue;
            BlockPos world = block.pos().offset(chunk.getPos().getMinBlockX(), 0, chunk.getPos().getMinBlockZ());
            if(chunk.getBlockEntity(world) instanceof KineticBlockEntity kinetic && Float.isFinite(kinetic.getSpeed())) speeds.put(block.pos(), kinetic.getSpeed());
        }
        return new HologramChunk(chunk.getPos(), grass, foliage, water, blocks, speeds);
    }

    // Ignore invisible light blocks when finding the visible terrain surface
    static int surfaceHeight(LevelChunk chunk, int x, int z, int minY){
        int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        while(y >= minY){
            var state = chunk.getBlockState(new BlockPos(chunk.getPos().getMinBlockX() + x, y, chunk.getPos().getMinBlockZ() + z));
            if(state == null || state.getRenderShape() != net.minecraft.world.level.block.RenderShape.INVISIBLE || !state.getFluidState().isEmpty()) break;
            y--;
        }
        return y;
    }

    // Cover tagged modded ores as well as ore blocks that omit the shared tag
    public static BlockState maskOre(BlockState state, int y, int surfaceY, boolean enabled){
        if(!enabled || y >= surfaceY) return state;
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if(!state.is(Tags.Blocks.ORES) && !id.endsWith("_ore") && !id.equals("ancient_debris")) return state;
        return (y < 0 || id.contains("deepslate") ? Blocks.DEEPSLATE : Blocks.STONE).defaultBlockState();
    }

}
