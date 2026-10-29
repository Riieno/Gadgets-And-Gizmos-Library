package com.rieno.gadgetsandgizmos.lib.client.render;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.lib.hologram.HologramChunk;
import com.rieno.gadgetsandgizmos.lib.hologram.HologramRegion;
import com.rieno.gadgetsandgizmos.lib.hologram.HologramSlice;
import com.rieno.gadgetsandgizmos.lib.hologram.HologramFaceVisibility;
import com.rieno.gadgetsandgizmos.lib.hologram.HologramResolution;
import com.rieno.gadgetsandgizmos.lib.hologram.SurfaceHologramSampler;
import com.rieno.gadgetsandgizmos.lib.geometry.ExteriorHull;
import com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

// Project tracked Sable bodies with cached exposed geometry and their live interpolated poses
public final class SubLevelHologramRenderer implements AutoCloseable{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        VARIABLES
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private final Map<UUID, Body> bodies = new HashMap<>();

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    public void render(PoseStack poses, MultiBufferSource buffers, Vec3 origin, HologramRegion region,
                       HologramSlice slice, boolean maskOres, float partialTick, long generation, HologramBlockEntityRenderer dynamic){
        render(poses, buffers, origin, region, slice, maskOres, partialTick, generation, dynamic, body -> true);
    }

    // Project only cached baked block models while retaining live ship transforms and ownership filters
    public void renderBaseModels(PoseStack poses, MultiBufferSource buffers, Vec3 origin, HologramRegion region,
                       HologramSlice slice, boolean maskOres, float partialTick, long generation,
                       Predicate<ClientSubLevel> visible){
        render(poses, buffers, origin, region, slice, maskOres, partialTick, generation, null, visible);
    }

    // Exclude bodies before sampling, allocating meshes or requesting dynamic visuals
    public void render(PoseStack poses, MultiBufferSource buffers, Vec3 origin, HologramRegion region,
                       HologramSlice slice, boolean maskOres, float partialTick, long generation,
                       HologramBlockEntityRenderer dynamic, Predicate<ClientSubLevel> visible){
        render(poses, buffers, origin, region, slice, maskOres, partialTick, generation, dynamic, visible, false, false, HologramResolution.FULL);
    }

    // Retain model data independently of animated partials and native visuals
    public void renderRetainedModels(PoseStack poses, MultiBufferSource buffers, Vec3 origin, HologramRegion region,
                       HologramSlice slice, boolean maskOres, float partialTick, long generation,
                       HologramBlockEntityRenderer dynamic, Predicate<ClientSubLevel> visible){
        render(poses, buffers, origin, region, slice, maskOres, partialTick, generation, dynamic, visible, true, false, HologramResolution.FULL);
    }

    // Keep only faces connected to the exterior, including interiors visible through transparent hull blocks
    public void renderExteriorModels(PoseStack poses, MultiBufferSource buffers, Vec3 origin, HologramRegion region,
                       HologramSlice slice, boolean maskOres, float partialTick, long generation,
                       HologramBlockEntityRenderer details, Predicate<ClientSubLevel> visible){
        renderExteriorModels(poses, buffers, origin, region, slice, maskOres, partialTick, generation, details, visible, HologramResolution.FULL);
    }

    public void renderExteriorModels(PoseStack poses, MultiBufferSource buffers, Vec3 origin, HologramRegion region,
                       HologramSlice slice, boolean maskOres, float partialTick, long generation,
                       HologramBlockEntityRenderer details, Predicate<ClientSubLevel> visible, HologramResolution resolution){
        render(poses, buffers, origin, region, slice, maskOres, partialTick, generation, details, visible, true, true, resolution);
    }

    private void render(PoseStack poses, MultiBufferSource buffers, Vec3 origin, HologramRegion region,
                        HologramSlice slice, boolean maskOres, float partialTick, long generation,
                        HologramBlockEntityRenderer dynamic, Predicate<ClientSubLevel> visible, boolean retainedModelData, boolean exterior, HologramResolution resolution){
        var mc = Minecraft.getInstance();
        if(mc.level == null) return;
        var bounds = region.bounds(slice.surface() ? -30_000_000 : slice.minY(), slice.surface() ? 30_000_000 : slice.maxY());
        var retained = new HashSet<UUID>();
        int budget = 2;
        int hullBudget = 1;
        for(var candidate : SableTransformApi.intersecting(mc.level, bounds)){
            if(!(candidate instanceof ClientSubLevel source) || source.isRemoved() || !visible.test(source)) continue;
            UUID id = source.getUniqueId();
            retained.add(id);
            Body body = bodies.get(id);
            if(body == null || body.liveModelData != (dynamic != null) || body.retainedModelData != retainedModelData || body.exterior != exterior){
                if(body != null) body.renderer.close();
                body = new Body(id, dynamic != null, retainedModelData, exterior);
                bodies.put(id, body);
            }
            long now = mc.level.getGameTime();
            if(body.slice == null || !body.slice.equals(slice) || body.masked != maskOres){ body.chunks.clear(); body.hull = null; body.slice = slice; body.masked = maskOres; }
            if(exterior && hullBudget > 0 && (body.hull == null || now - body.hullTick >= 20 || !region.equals(body.hullRegion))){
                body.hull = hull(source, region, slice, partialTick); body.hullTick = now; body.hullRegion = region; hullBudget--;
            }
            if(exterior && body.hull == null) continue;
            var loaded = new HashSet<Long>();
            for(var holder : source.getPlot().getLoadedChunks()){
                LevelChunk chunk = holder.getChunk();
                if(chunk == null) continue;
                long key = chunk.getPos().toLong(); loaded.add(key);
                Cached prev = body.chunks.get(key);
                if(budget > 0 && (prev == null || now - prev.tick >= 20)){
                    HologramChunk sampled = sample(source, chunk, region, slice, maskOres, partialTick, body.hull);
                    body.chunks.put(key, new Cached(now, prev != null && sampled.hasSameSurface(prev.chunk) ? prev.chunk : sampled));
                    budget--;
                }
            }
            body.chunks.keySet().retainAll(loaded);
            BlockPos anchor = source.getPlot().getCenterBlock();
            var frame = SubLevelClientRenderApi.worldFrame(mc.level, anchor.getCenter(), source, partialTick);
            poses.pushPose();
            try{
                poses.translate(frame.position().x - origin.x, frame.position().y - origin.y, frame.position().z - origin.z);
                poses.mulPose(frame.orientation());
                poses.scale((float)frame.scale().x, (float)frame.scale().y, (float)frame.scale().z);
                List<HologramChunk> chunks = body.chunks.values().stream().map(Cached::chunk).toList();
                body.renderer.render(poses, buffers, anchor.getCenter(), chunks, generation, resolution);
                if(dynamic != null) dynamic.render(poses, buffers, anchor.getCenter(), chunks, id, partialTick);
            }finally{ poses.popPose(); }
        }
        bodies.entrySet().removeIf(entry -> {
            if(retained.contains(entry.getKey())) return false;
            entry.getValue().renderer.close(); return true;
        });
    }

    // Slice tests use root-world coordinates, while face culling stays in the body's local grid
    private static HologramChunk sample(ClientSubLevel body, LevelChunk chunk, HologramRegion region,
                                        HologramSlice slice, boolean maskOres, float tick, ExteriorHull hull){
        var mc = Minecraft.getInstance();
        List<HologramChunk.SurfaceBlock> blocks = new ArrayList<>();
        var bounds = region.bounds(slice.surface() ? -30_000_000 : slice.minY(), slice.surface() ? 30_000_000 : slice.maxY());
        BlockPos anchor = body.getPlot().getCenterBlock();
        var frame = SubLevelClientRenderApi.worldFrame(mc.level, anchor.getCenter(), body, tick);
        var rotation = new org.joml.Quaterniond(frame.orientation());
        var point = new org.joml.Vector3d();
        var offsets = new org.joml.Vector3d[6];
        for(Direction dir : Direction.values()) offsets[dir.ordinal()] = rotation.transform(new org.joml.Vector3d(
                dir.getStepX() * frame.scale().x, dir.getStepY() * frame.scale().y, dir.getStepZ() * frame.scale().z));
        chunk.findBlocks(state -> !state.isAir(), (pos, state) -> {
            if(blocks.size() >= HologramChunk.MAX_BLOCKS) return;
            point.set((pos.getX() - anchor.getX()) * frame.scale().x,
                    (pos.getY() - anchor.getY()) * frame.scale().y, (pos.getZ() - anchor.getZ()) * frame.scale().z);
            rotation.transform(point).add(frame.position().x, frame.position().y, frame.position().z);
            if(!bounds.contains(point.x, point.y, point.z)) return;
            int faces = 0;
            int exposed = hull == null ? 63 : hull.visibleFaces(pos.getX(), pos.getY(), pos.getZ());
            for(Direction dir : Direction.values()){
                var delta = offsets[dir.ordinal()];
                boolean included = bounds.contains(point.x + delta.x, point.y + delta.y, point.z + delta.z);
                if(included && (exposed & 1 << dir.ordinal()) == 0) continue;
                if(HologramFaceVisibility.shouldRenderFace(state, body.getLevel(), pos, dir, included)) faces |= 1 << dir.ordinal();
            }
            if(faces == 0) return;
            int surface = mc.level.hasChunkAt(BlockPos.containing(point.x, point.y, point.z))
                    ? mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, (int)Math.floor(point.x), (int)Math.floor(point.z)) : Integer.MAX_VALUE;
            var shown = SurfaceHologramSampler.maskOre(state, (int)Math.floor(point.y), surface, maskOres);
            blocks.add(new HologramChunk.SurfaceBlock(new BlockPos(pos.getX() & 15, pos.getY(), pos.getZ() & 15), shown, faces));
        });
        return new HologramChunk(chunk.getPos(), 0x79A94E, 0x48A13B, 0x3F76E4, blocks);
    }

    // Snapshot opaque cells on the render thread; the flood fill only reads detached coordinates
    private static ExteriorHull hull(ClientSubLevel body, HologramRegion region, HologramSlice slice, float partial){
        var opaque = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        int[] min = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE}, max = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        var bounds = region.bounds(slice.surface() ? -30_000_000 : slice.minY(), slice.surface() ? 30_000_000 : slice.maxY());
        var anchor = body.getPlot().getCenterBlock();
        var frame = SubLevelClientRenderApi.worldFrame(body.getLevel(), anchor.getCenter(), body, partial);
        var rotation = new org.joml.Quaterniond(frame.orientation());
        var point = new org.joml.Vector3d();
        for(var holder : body.getPlot().getLoadedChunks()){
            var chunk = holder.getChunk(); if(chunk == null) continue;
            chunk.findBlocks(state -> !state.isAir(), (pos, state) -> {
                min[0] = Math.min(min[0], pos.getX()); min[1] = Math.min(min[1], pos.getY()); min[2] = Math.min(min[2], pos.getZ());
                max[0] = Math.max(max[0], pos.getX()); max[1] = Math.max(max[1], pos.getY()); max[2] = Math.max(max[2], pos.getZ());
                point.set((pos.getX() - anchor.getX()) * frame.scale().x, (pos.getY() - anchor.getY()) * frame.scale().y, (pos.getZ() - anchor.getZ()) * frame.scale().z);
                rotation.transform(point).add(frame.position().x, frame.position().y, frame.position().z);
                if(bounds.contains(point.x, point.y, point.z) && HologramBlockModels.opaque(state, body.getLevel(), pos)) opaque.add(pos.asLong());
            });
        }
        if(min[0] == Integer.MAX_VALUE) return new ExteriorHull(0, 0, 0, 0, 0, 0, (x, y, z) -> false);
        return new ExteriorHull(min[0], min[1], min[2], max[0], max[1], max[2], (x, y, z) -> opaque.contains(BlockPos.asLong(x, y, z)));
    }

    @Override public void close(){ bodies.values().forEach(body -> body.renderer.close()); bodies.clear(); }
    private record Cached(long tick, HologramChunk chunk){}
    private static final class Body{
        private final HologramProjectionRenderer renderer;
        private final boolean liveModelData;
        private final boolean retainedModelData;
        private final boolean exterior;
        private ExteriorHull hull;
        private long hullTick;
        private HologramRegion hullRegion;
        private final Map<Long, Cached> chunks = new HashMap<>();
        private HologramSlice slice;
        private boolean masked;
        private Body(UUID id, boolean liveModelData, boolean retainedModelData, boolean exterior){
            this.liveModelData = liveModelData;
            this.retainedModelData = retainedModelData;
            this.exterior = exterior;
            renderer = exterior ? HologramProjectionRenderer.staticModels(id) : retainedModelData ? HologramProjectionRenderer.retainedModels(id) : new HologramProjectionRenderer(id, liveModelData);
        }
    }
}
