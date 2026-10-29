package com.rieno.gadgetsandgizmos.lib.client.render;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.rieno.gadgetsandgizmos.lib.geometry.ClipBounds;
import org.joml.Matrix4f;
import com.rieno.gadgetsandgizmos.lib.hologram.HologramChunk;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import dev.ryanhcode.sable.sublevel.SubLevel;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

// Share native Flywheel visuals and ordinary partial renderers between terrain and ship projections
public final class HologramBlockEntityRenderer implements AutoCloseable{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        VARIABLES
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private final FlywheelHologramVisuals flywheel;
    private final java.util.function.Predicate<BlockEntity> allowed;
    private final boolean animated;
    private final Map<Key, ChunkEntries> chunks = new HashMap<>();
    private long frame;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    public HologramBlockEntityRenderer(Function<Float, HologramProjectionFrame> frame){
        this(frame, entity -> true);
    }

    public HologramBlockEntityRenderer(Function<Float, HologramProjectionFrame> frame, java.util.function.Predicate<BlockEntity> allowed){
        this(frame, allowed, true);
    }

    // Static projections retain native partials and record ordinary renderer geometry once
    public HologramBlockEntityRenderer(Function<Float, HologramProjectionFrame> frame, java.util.function.Predicate<BlockEntity> allowed, boolean animated){
        this.allowed = allowed;
        this.animated = animated;
        flywheel = new FlywheelHologramVisuals(Minecraft.getInstance().level, frame, animated);
    }

    public void beginFrame(){ frame++; flywheel.beginFrame(); }
    public void endFrame(){
        flywheel.endFrame();
        chunks.values().removeIf(chunk -> {
            if(chunk.seen == frame) return false;
            chunk.close(); return true;
        });
    }

    // Only exposed blocks can request a visual or invoke a block entity renderer
    public void render(PoseStack poses, MultiBufferSource buffers, Vec3 origin,
                       Collection<HologramChunk> chunks, UUID body, float partialTick){
        try(var scope = HologramRenderScope.enter()){
            renderBlocks(poses, buffers, origin, chunks, body, partialTick);
        }
    }

    private void renderBlocks(PoseStack poses, MultiBufferSource buffers, Vec3 origin,
                              Collection<HologramChunk> chunks, UUID body, float partialTick){
        var mc = Minecraft.getInstance();
        if(mc.level == null) return;
        SubLevel source = body == null ? null : SableLevelApi.subLevel(mc.level, body);
        if(body != null && (source == null || source.isRemoved())) return;
        var clip = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).mul(poses.last().pose());
        Map<VertexConsumer, Tint> tinted = new IdentityHashMap<>();
        MultiBufferSource ghost = type -> {
            VertexConsumer target = buffers.getBuffer(type.format() == DefaultVertexFormat.BLOCK ? HologramRenderTypes.terrain() : type);
            return tinted.computeIfAbsent(target, Tint::new);
        };
        for(var chunk : chunks){
            Key key = new Key(body, chunk.pos().toLong());
            ChunkEntries cached = this.chunks.get(key);
            if(cached == null || !cached.source.hasSameSurface(chunk)){
                if(cached != null) cached.close();
                cached = new ChunkEntries(chunk);
                this.chunks.put(key, cached);
            }
            cached.source = chunk;
            cached.seen = frame;
            if(cached.entries.isEmpty()) continue;
            LevelChunk loaded = loadedChunk(chunk, source);
            for(var entry : cached.entries){
                var block = entry.block;
                BlockPos pos = entry.pos;
                BlockEntity entity = loaded == null ? null : loaded.getBlockEntities().get(pos);
                if((entity == null || entity.isRemoved()) && source != null) entity = SubLevelBlockEntityCollector.getBlockEntity(source, pos);
                if(entity != null && entity.isRemoved()) entity = null;
                if(entity == null || entity.getBlockState() != block.state()){
                    if(!(block.state().getBlock() instanceof EntityBlock factory)) continue;
                    entity = entry.detached;
                    if(entity == null){
                        entity = factory.newBlockEntity(pos, block.state());
                        if(entity == null) continue;
                        entity.setLevel(mc.level);
                        if(entity instanceof SmartBlockEntity smart) smart.markVirtual();
                        entry.detached = entity;
                    }
                    if(entity instanceof KineticBlockEntity kinetic) kinetic.setSpeed(chunk.kineticSpeeds().getOrDefault(block.pos(), 0F));
                }
                if(!allowed.test(entity)) continue;
                if(entry.entity != entity){
                    entry.close();
                    entry.entity = entity;
                    BlockEntityRenderer<BlockEntity> renderer = mc.getBlockEntityRenderDispatcher().getRenderer(entity);
                    if(renderer instanceof HologramRendererProvider provider) entry.retained = provider.createHologramRenderer(entity);
                }
                var renderer = mc.getBlockEntityRenderDispatcher().getRenderer(entity);
                if(renderer != null && !renderer.shouldRenderOffScreen(entity)){
                    var bounds = renderer.getRenderBoundingBox(entity);
                    if(!ClipBounds.intersects(clip, bounds.minX - origin.x, bounds.minY - origin.y, bounds.minZ - origin.z,
                            bounds.maxX - origin.x, bounds.maxY - origin.y, bounds.maxZ - origin.z)) continue;
                }
                boolean nativeVisual = entry.retained == null && flywheel.request(entity, block.state());
                if(nativeVisual && dev.engine_room.flywheel.lib.visualization.VisualizationHelper.skipVanillaRender(entity)) continue;
                poses.pushPose();
                poses.translate(pos.getX() - origin.x, pos.getY() - origin.y, pos.getZ() - origin.z);
                try{
                    if(!animated){
                        BlockEntity target = entity;
                        entry.staticGeometry.render(poses, ghost, !entry.recorded,
                                output -> draw(entry, target, block.state(), new PoseStack(), output, nativeVisual, partialTick));
                        entry.recorded = true;
                        continue;
                    }
                    if(entry.retained != null){
                        entry.retained.render(partialTick, poses, ghost, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                        continue;
                    }
                    boolean partial = !nativeVisual && CreateKineticPreviewRenderer.render(entity, block.state(), poses, ghost, LightTexture.FULL_BRIGHT);
                    if(!partial) mc.getBlockEntityRenderDispatcher().renderItem(entity, poses, ghost, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                }finally{ poses.popPose(); }
            }
        }
    }

    private static void draw(Entry entry, BlockEntity entity, net.minecraft.world.level.block.state.BlockState state,
                             PoseStack poses, MultiBufferSource buffers, boolean nativeVisual, float partialTick){
        if(entry.retained != null){ entry.retained.render(partialTick, poses, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY); return; }
        if(!nativeVisual && CreateKineticPreviewRenderer.render(entity, state, poses, buffers, LightTexture.FULL_BRIGHT)) return;
        Minecraft.getInstance().getBlockEntityRenderDispatcher().renderItem(entity, poses, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
    }

    // Resolve each loaded source chunk once; actor-only entities retain the existing exact lookup
    private static LevelChunk loadedChunk(HologramChunk chunk, SubLevel source){
        var mc = Minecraft.getInstance();
        if(source != null){
            var plot = source.getPlot();
            var holder = plot.getChunkHolder(plot.toLocal(chunk.pos()));
            return holder == null ? null : holder.getChunk();
        }
        BlockPos pos = new BlockPos(chunk.pos().getMinBlockX(), 0, chunk.pos().getMinBlockZ());
        if(SubLevelBlockEntityCollector.isSubLevelPlotPosition(mc.level, pos)) return null;
        return mc.level.getChunkSource().getChunk(chunk.pos().x, chunk.pos().z, ChunkStatus.FULL, false);
    }

    @Override public void close(){ flywheel.close(); chunks.values().forEach(ChunkEntries::close); chunks.clear(); }
    private record Key(UUID body, long chunk){}

    private static final class ChunkEntries{
        private HologramChunk source;
        private long seen;
        private final List<Entry> entries;
        private ChunkEntries(HologramChunk source){
            this.source = source;
            entries = source.blocks().stream().filter(block -> block.state().hasBlockEntity())
                    .map(block -> new Entry(block, block.pos().offset(source.pos().getMinBlockX(), 0, source.pos().getMinBlockZ()))).toList();
        }
        private void close(){ entries.forEach(Entry::close); }
    }

    private static final class Entry{
        private final HologramChunk.SurfaceBlock block;
        private final BlockPos pos;
        private BlockEntity detached;
        private BlockEntity entity;
        private RetainedHologramRenderer retained;
        private final RenderGeometryCache staticGeometry = new RenderGeometryCache();
        private boolean recorded;
        private Entry(HologramChunk.SurfaceBlock block, BlockPos pos){ this.block = block; this.pos = pos; }
        private void close(){ if(retained != null) retained.close(); retained = null; entity = null; staticGeometry.clear(); recorded = false; }
    }

    record Tint(VertexConsumer target) implements VertexConsumer{
        @Override public void addVertex(float x, float y, float z, int color, float u, float v,
                                        int overlay, int light, float normalX, float normalY, float normalZ){
            int r = ((color >> 16 & 255) + 50) / 2;
            int g = ((color >> 8 & 255) + 170) / 2;
            int b = ((color & 255) + 240) / 2;
            int a = (color >>> 24) * 3 / 4;
            target.addVertex(x, y, z, a << 24 | r << 16 | g << 8 | b, u, v, overlay,
                    LightTexture.FULL_BRIGHT, normalX, normalY, normalZ);
        }
        @Override public VertexConsumer addVertex(float x, float y, float z){ target.addVertex(x, y, z); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a){ target.setColor((r + 50) / 2, (g + 170) / 2, (b + 240) / 2, a * 3 / 4); return this; }
        @Override public VertexConsumer setUv(float u, float v){ target.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v){ target.setUv1(u, v); return this; }
        @Override public VertexConsumer setUv2(int u, int v){ target.setUv2(240, 240); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z){ target.setNormal(x, y, z); return this; }
    }
}
