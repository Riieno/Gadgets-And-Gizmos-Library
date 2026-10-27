package com.rieno.gadgetsandgizmos.lib.client.render;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.rieno.gadgetsandgizmos.lib.hologram.HologramChunk;
import com.rieno.gadgetsandgizmos.lib.probe.BlockEntityLookupApi;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
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
    private final Map<Key, BlockEntity> detached = new HashMap<>();
    private final Map<HologramChunk, List<HologramChunk.SurfaceBlock>> visible = new IdentityHashMap<>();

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    public HologramBlockEntityRenderer(Function<Float, HologramProjectionFrame> frame){
        this(frame, entity -> true);
    }

    public HologramBlockEntityRenderer(Function<Float, HologramProjectionFrame> frame, java.util.function.Predicate<BlockEntity> allowed){
        this.allowed = allowed;
        flywheel = new FlywheelHologramVisuals(Minecraft.getInstance().level, frame);
    }

    public void beginFrame(){ flywheel.beginFrame(); }
    public void endFrame(){ flywheel.endFrame(); }

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
        MultiBufferSource ghost = type -> new Tint(buffers.getBuffer(type.format() == DefaultVertexFormat.BLOCK ? HologramRenderTypes.terrain() : type));
        for(var chunk : chunks){
            var dynamic = visible.computeIfAbsent(chunk, key -> key.blocks().stream().filter(block -> block.state().hasBlockEntity()).toList());
            for(var block : dynamic){
                BlockPos pos = block.pos().offset(chunk.pos().getMinBlockX(), 0, chunk.pos().getMinBlockZ());
                BlockEntity entity = BlockEntityLookupApi.findLoadedExact(mc.level, body, pos);
                if(entity == null || entity.getBlockState() != block.state()){
                    if(!(block.state().getBlock() instanceof EntityBlock factory)) continue;
                    Key key = new Key(body, pos);
                    entity = detached.get(key);
                    if(entity == null || entity.getBlockState() != block.state()){
                        entity = factory.newBlockEntity(pos, block.state());
                        if(entity == null) continue;
                        entity.setLevel(mc.level);
                        if(entity instanceof SmartBlockEntity smart) smart.markVirtual();
                        detached.put(key, entity);
                    }
                    if(entity instanceof KineticBlockEntity kinetic) kinetic.setSpeed(chunk.kineticSpeeds().getOrDefault(block.pos(), 0F));
                }
                if(!allowed.test(entity)) continue;
                boolean nativeVisual = flywheel.request(entity, block.state());
                if(nativeVisual && dev.engine_room.flywheel.lib.visualization.VisualizationHelper.skipVanillaRender(entity)) continue;
                poses.pushPose();
                poses.translate(pos.getX() - origin.x, pos.getY() - origin.y, pos.getZ() - origin.z);
                try{
                    boolean partial = !nativeVisual && CreateKineticPreviewRenderer.render(entity, block.state(), poses, ghost, LightTexture.FULL_BRIGHT);
                    if(!partial) mc.getBlockEntityRenderDispatcher().renderItem(entity, poses, ghost, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                }finally{ poses.popPose(); }
            }
        }
        if(visible.size() > 1024) visible.clear();
        if(detached.size() > 4096) detached.clear();
    }

    @Override public void close(){ flywheel.close(); detached.clear(); visible.clear(); }
    private record Key(UUID body, BlockPos pos){}

    private record Tint(VertexConsumer target) implements VertexConsumer{
        @Override public VertexConsumer addVertex(float x, float y, float z){ target.addVertex(x, y, z); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a){ target.setColor((r + 50) / 2, (g + 170) / 2, (b + 240) / 2, a * 3 / 4); return this; }
        @Override public VertexConsumer setUv(float u, float v){ target.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v){ target.setUv1(u, v); return this; }
        @Override public VertexConsumer setUv2(int u, int v){ target.setUv2(240, 240); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z){ target.setNormal(x, y, z); return this; }
    }
}
