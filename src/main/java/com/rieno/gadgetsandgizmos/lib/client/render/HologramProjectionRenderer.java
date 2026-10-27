package com.rieno.gadgetsandgizmos.lib.client.render;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rieno.gadgetsandgizmos.lib.hologram.HologramChunk;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

// Cache exposed textured terrain on the GPU and reject chunks outside the projected view
public final class HologramProjectionRenderer implements AutoCloseable{
    /*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        CONSTANTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private static final int MESHES_PER_FRAME = 2;
    private static final float ALPHA = 0.72F;

    /*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        VARIABLES
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private final Map<Long, ChunkMesh> meshes = new HashMap<>();
    private final Map<Long, ChunkBounds> bounds = new HashMap<>();
    private final Map<Long, RenderSurfaceVisibility> visibility = new HashMap<>();
    private long resourceGeneration = -1;
    private final java.util.UUID sourceBody;

    public HologramProjectionRenderer(){ this(null); }
    public HologramProjectionRenderer(java.util.UUID sourceBody){ this.sourceBody = sourceBody; }

    /*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // The caller supplies its projection transform and source-world origin
    public void render(PoseStack poses, Vec3 origin, Collection<HologramChunk> chunks, long generation){
        render(poses, null, origin, chunks, generation);
    }

    // Passing world buffers also enables asynchronous occlusion probes for whole chunks
    public void render(PoseStack poses, MultiBufferSource buffers, Vec3 origin, Collection<HologramChunk> chunks, long generation){
        try(var scope = HologramRenderScope.enter()){
            renderTerrain(poses, buffers, origin, chunks, generation);
        }
    }

    private void renderTerrain(PoseStack poses, MultiBufferSource buffers, Vec3 origin, Collection<HologramChunk> chunks, long generation){
        if(resourceGeneration != generation){ close(); resourceGeneration = generation; }
        var retained = new HashSet<Long>();
        int remaining = MESHES_PER_FRAME;
        var type = HologramRenderTypes.terrain();
        try{
            for(HologramChunk chunk : chunks){
                long key = chunk.pos().toLong();
                retained.add(key);
                poses.pushPose();
                try{
                    poses.translate(chunk.pos().getMinBlockX() - origin.x, -origin.y,
                            chunk.pos().getMinBlockZ() - origin.z);
                    if(chunk.blocks().isEmpty()){
                        ChunkMesh old = meshes.remove(key);
                        if(old != null) old.close();
                        continue;
                    }
                    ChunkBounds box = bounds.get(key);
                    if(box == null || box.source != chunk){
                        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
                        for(var block : chunk.blocks()){
                            minY = Math.min(minY, block.pos().getY());
                            maxY = Math.max(maxY, block.pos().getY() + 1);
                        }
                        box = new ChunkBounds(chunk, minY, maxY);
                        bounds.put(key, box);
                    }
                    Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(poses.last().pose());
                    var frustum = new FrustumIntersection(new Matrix4f(RenderSystem.getProjectionMatrix()).mul(view));
                    if(!frustum.testAab(0, box.minY, 0, 16, box.maxY, 16)) continue;
                    if(buffers != null && !visibility.computeIfAbsent(key, ignored -> new RenderSurfaceVisibility())
                            .probe(poses, buffers, new AABB(0, box.minY, 0, 16, box.maxY, 16))) continue;
                    ChunkMesh mesh = meshes.get(key);
                    if((mesh == null || !mesh.source.equals(chunk) || mesh.dynamic && Minecraft.getInstance().level.getGameTime() - mesh.builtAt >= 20) && remaining > 0){
                        if(mesh != null) mesh.close();
                        mesh = build(chunk);
                        meshes.put(key, mesh);
                        remaining--;
                    }
                    if(mesh == null || mesh.buffer == null) continue;
                    type.setupRenderState();
                    try{
                        mesh.buffer.bind();
                        mesh.buffer.drawWithShader(view, RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
                    }finally{ type.clearRenderState(); }
                }finally{ poses.popPose(); }
            }
        }finally{
            VertexBuffer.unbind();
        }
        visibility.entrySet().removeIf(entry -> {
            if(retained.contains(entry.getKey())) return false;
            entry.getValue().close();
            return true;
        });
        bounds.keySet().retainAll(retained);
        meshes.entrySet().removeIf(entry -> {
            if(retained.contains(entry.getKey())) return false;
            entry.getValue().close();
            return true;
        });
    }

    // Release buffers on world changes, resource reloads and owner removal
    @Override
    public void close(){
        meshes.values().forEach(ChunkMesh::close);
        meshes.clear();
        bounds.clear();
        visibility.values().forEach(RenderSurfaceVisibility::close);
        visibility.clear();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        HELPERS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Bake each block once and discard its occluded directional quads before upload
    private ChunkMesh build(HologramChunk chunk){
        Minecraft mc = Minecraft.getInstance();
        PoseStack poses = new PoseStack();
        try(ByteBufferBuilder bytes = new ByteBufferBuilder(262144)){
            BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            RandomSource random = RandomSource.create();
            for(var block : chunk.blocks()){
                poses.pushPose();
                poses.translate(block.pos().getX(), block.pos().getY(), block.pos().getZ());
                if(block.state().getRenderShape() == RenderShape.MODEL){
                    var model = mc.getBlockRenderer().getBlockModel(block.state());
                    var worldPos = block.pos().offset(chunk.pos().getMinBlockX(), 0, chunk.pos().getMinBlockZ());
                    var entity = block.state().hasBlockEntity() ? com.rieno.gadgetsandgizmos.lib.probe.BlockEntityLookupApi.findLoadedExact(mc.level, sourceBody, worldPos) : null;
                    ModelData data = entity == null || entity.getBlockState() != block.state() ? ModelData.EMPTY : entity.getModelData();
                    if(entity != null && entity.getBlockState() == block.state()) data = model.getModelData(entity.getLevel(), worldPos, block.state(), data);
                    random.setSeed(block.state().getSeed(block.pos()));
                    for(var layer : model.getRenderTypes(block.state(), random, data)){
                        for(Direction dir : Direction.values()){
                            if((block.faces() & 1 << dir.ordinal()) == 0) continue;
                            random.setSeed(block.state().getSeed(block.pos()));
                            quads(builder, poses, model.getQuads(block.state(), dir, random, data, layer), block, chunk);
                        }
                        random.setSeed(block.state().getSeed(block.pos()));
                        quads(builder, poses, model.getQuads(block.state(), null, random, data, layer), block, chunk);
                    }
                }
                if(!block.state().getFluidState().isEmpty()) fluid(builder, poses, block, chunk);
                poses.popPose();
            }
            MeshData data = builder.build();
            if(data == null) return new ChunkMesh(chunk, null);
            VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            buffer.bind();
            buffer.upload(data);
            VertexBuffer.unbind();
            return new ChunkMesh(chunk, buffer);
        }
    }

    // Apply biome colors before the common hologram tint
    private static void quads(VertexConsumer out, PoseStack poses, List<BakedQuad> quads,
                              HologramChunk.SurfaceBlock block, HologramChunk chunk){
        for(var quad : quads){
            int color = 0xFFFFFF;
            if(quad.isTinted()){
                color = block.state().is(BlockTags.LEAVES) ? chunk.foliageColor() :
                        Minecraft.getInstance().getBlockColors().getColor(block.state(), null, null, quad.getTintIndex());
                if(color == -1 || block.state().is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)
                        || block.state().is(net.minecraft.world.level.block.Blocks.SUGAR_CANE) || block.state().is(BlockTags.REPLACEABLE_BY_TREES)) color = chunk.grassColor();
            }
            float shade = quad.getDirection() == Direction.UP ? 1 : quad.getDirection() == Direction.DOWN ? 0.65F : 0.85F;
            out.putBulkData(poses.last(), quad, ((color >> 16 & 255) / 255F * 0.45F + 0.12F) * shade,
                    ((color >> 8 & 255) / 255F * 0.7F + 0.2F) * shade,
                    ((color & 255) / 255F * 0.65F + 0.35F) * shade, ALPHA,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
    }

    // Render fluid surfaces without invoking a live level's fluid tessellator
    private static void fluid(VertexConsumer out, PoseStack poses, HologramChunk.SurfaceBlock block, HologramChunk chunk){
        boolean water = block.state().getFluidState().is(FluidTags.WATER);
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS)
                .apply(ResourceLocation.withDefaultNamespace(water ? "block/water_still" : "block/lava_still"));
        int color = water ? chunk.waterColor() : 0xFFB345;
        for(Direction dir : Direction.values()){
            if((block.faces() & 1 << dir.ordinal()) == 0) continue;
            float[][] corners = switch(dir){
                case UP -> new float[][]{{0,1,1},{1,1,1},{1,1,0},{0,1,0}};
                case DOWN -> new float[][]{{0,0,0},{1,0,0},{1,0,1},{0,0,1}};
                case NORTH -> new float[][]{{1,0,0},{0,0,0},{0,1,0},{1,1,0}};
                case SOUTH -> new float[][]{{0,0,1},{1,0,1},{1,1,1},{0,1,1}};
                case WEST -> new float[][]{{0,0,0},{0,0,1},{0,1,1},{0,1,0}};
                case EAST -> new float[][]{{1,0,1},{1,0,0},{1,1,0},{1,1,1}};
            };
            for(int idx = 0; idx < 4; idx++){
                float[] pt = corners[idx];
                out.addVertex(poses.last(), pt[0], pt[1], pt[2])
                        .setColor((color >> 16 & 255) / 2 + 30, (color >> 8 & 255) / 2 + 80, (color & 255) / 2 + 120, 190)
                        .setUv(idx == 0 || idx == 3 ? sprite.getU0() : sprite.getU1(), idx < 2 ? sprite.getV1() : sprite.getV0())
                        .setLight(LightTexture.FULL_BRIGHT).setNormal(poses.last(), dir.getStepX(), dir.getStepY(), dir.getStepZ());
            }
        }
    }

    private record ChunkBounds(HologramChunk source, int minY, int maxY){}

    private record ChunkMesh(HologramChunk source, VertexBuffer buffer, long builtAt, boolean dynamic){
        private ChunkMesh(HologramChunk source, VertexBuffer buffer){
            this(source, buffer, Minecraft.getInstance().level.getGameTime(), source.blocks().stream().anyMatch(block -> block.state().hasBlockEntity()));
        }
        private void close(){ if(buffer != null) buffer.close(); }
    }
}
