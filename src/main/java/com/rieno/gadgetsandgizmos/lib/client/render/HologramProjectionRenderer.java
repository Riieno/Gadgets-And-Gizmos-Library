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
import com.rieno.gadgetsandgizmos.lib.hologram.HologramResolution;
import com.rieno.gadgetsandgizmos.lib.geometry.SurfaceColorMesh;
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
    private final boolean liveModelData;
    private final boolean retainedModelData;
    private final boolean staticModels;

    public HologramProjectionRenderer(){ this(null); }
    public HologramProjectionRenderer(java.util.UUID sourceBody){ this(sourceBody, true); }

    // Base models need no live block entity lookup or periodic model-data refresh
    public HologramProjectionRenderer(java.util.UUID sourceBody, boolean liveModelData){
        this(sourceBody, liveModelData, false);
    }

    private HologramProjectionRenderer(java.util.UUID sourceBody, boolean liveModelData, boolean retainedModelData){
        this(sourceBody, liveModelData, retainedModelData, false);
    }

    private HologramProjectionRenderer(java.util.UUID sourceBody, boolean liveModelData, boolean retainedModelData, boolean staticModels){
        this.sourceBody = sourceBody;
        this.liveModelData = liveModelData;
        this.retainedModelData = retainedModelData;
        this.staticModels = staticModels;
    }

    public static HologramProjectionRenderer baseModels(){ return new HologramProjectionRenderer(null, false); }

    // Retain live baked model data until the source requests a normal model-data refresh
    public static HologramProjectionRenderer retainedModels(java.util.UUID sourceBody){
        return new HologramProjectionRenderer(sourceBody, true, true);
    }

    // Closed tank housings use source connectivity without rendering their contents
    public static HologramProjectionRenderer staticModels(java.util.UUID sourceBody){
        return new HologramProjectionRenderer(sourceBody, true, true, true);
    }

    public static void clearColorCache(){ HologramSurfaceColors.clear(); }

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
        render(poses, buffers, origin, chunks, generation, HologramResolution.FULL);
    }

    public void render(PoseStack poses, MultiBufferSource buffers, Vec3 origin, Collection<HologramChunk> chunks, long generation, HologramResolution resolution){
        try(var scope = HologramRenderScope.enter()){
            renderTerrain(poses, buffers, origin, chunks, generation, resolution);
        }
    }

    private void renderTerrain(PoseStack poses, MultiBufferSource buffers, Vec3 origin, Collection<HologramChunk> chunks, long generation, HologramResolution resolution){
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
                    var clip = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(view);
                    var frustum = new FrustumIntersection(clip);
                    if(!frustum.testAab(0, box.minY, 0, 16, box.maxY, 16)) continue;
                    if(buffers != null && !visibility.computeIfAbsent(key, ignored -> new RenderSurfaceVisibility())
                            .probe(poses, buffers, new AABB(0, box.minY, 0, 16, box.maxY, 16))) continue;
                    ChunkMesh mesh = meshes.get(key);
                    var window = Minecraft.getInstance().getWindow();
                    int level = resolution == HologramResolution.FULL ? 0 : resolution.level(ProjectionDetail.blockPixels(clip,
                            box.minY, box.maxY, window.getWidth(), window.getHeight()), mesh == null ? -1 : mesh.level);
                    boolean changed = mesh == null || (liveModelData && !retainedModelData ? !mesh.source.equals(chunk) : !mesh.source.hasSameSurface(chunk));
                    if((changed || mesh.level != level || mesh.modelDataChanged() || mesh.dynamic && Minecraft.getInstance().level.getGameTime() - mesh.builtAt >= 20) && remaining > 0){
                        if(mesh != null) mesh.close();
                        mesh = build(chunk, level);
                        meshes.put(key, mesh);
                        remaining--;
                    }
                    if(mesh != null && !changed && mesh.source != chunk){
                        mesh = new ChunkMesh(chunk, mesh.buffer, mesh.builtAt, mesh.dynamic, mesh.modelData, mesh.level, mesh.colorQuads);
                        meshes.put(key, mesh);
                    }
                    if(mesh == null) continue;
                    draw(mesh.buffer, type, view);
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

    private static void draw(VertexBuffer buffer, net.minecraft.client.renderer.RenderType type, Matrix4f view){
        if(buffer == null) return;
        type.setupRenderState();
        try{ buffer.bind(); buffer.drawWithShader(view, RenderSystem.getProjectionMatrix(), RenderSystem.getShader()); }
        finally{ type.clearRenderState(); }
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
        return build(chunk, 0);
    }

    private ChunkMesh build(HologramChunk chunk, int level){
        Minecraft mc = Minecraft.getInstance();
        PoseStack poses = new PoseStack();
        Map<net.minecraft.core.BlockPos, ModelData> modelData = new HashMap<>();
        var cells = new java.util.ArrayList<SurfaceColorMesh.Cell>();
        try(ByteBufferBuilder bytes = new ByteBufferBuilder(262144)){
            BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            RandomSource random = RandomSource.create();
            for(var source : chunk.blocks()){
                var block = staticModels ? new HologramChunk.SurfaceBlock(source.pos(), HologramBlockModels.closedTank(source.state()), source.faces()) : source;
                if(level > 0 && HologramSurfaceColors.collect(block, chunk, random, cells)) continue;
                poses.pushPose();
                poses.translate(block.pos().getX(), block.pos().getY(), block.pos().getZ());
                if(block.state().getRenderShape() == RenderShape.MODEL){
                    var model = mc.getBlockRenderer().getBlockModel(block.state());
                    var worldPos = block.pos().offset(chunk.pos().getMinBlockX(), 0, chunk.pos().getMinBlockZ());
                    if(retainedModelData && block.state().hasBlockEntity()) modelData.put(worldPos, mc.level.getModelDataManager().getAt(worldPos));
                    var entity = liveModelData && block.state().hasBlockEntity() ? com.rieno.gadgetsandgizmos.lib.probe.BlockEntityLookupApi.findLoadedExact(mc.level, sourceBody, worldPos) : null;
                    ModelData data = entity == null || entity.getBlockState() != source.state() ? ModelData.EMPTY : entity.getModelData();
                    if(entity != null && entity.getBlockState() == source.state()) data = model.getModelData(entity.getLevel(), worldPos, block.state(), data);
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
            int colorQuads = colors(builder, cells, level);
            return new ChunkMesh(chunk, upload(builder.build()), liveModelData && !retainedModelData, modelData, level, colorQuads);
        }
    }

    private static VertexBuffer upload(MeshData data){
        if(data == null) return null;
        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        try{ buffer.bind(); buffer.upload(data); return buffer; }
        catch(RuntimeException | Error err){ buffer.close(); throw err; }
        finally{ VertexBuffer.unbind(); }
    }

    private static int colors(VertexConsumer builder, List<SurfaceColorMesh.Cell> cells, int level){
        if(cells.isEmpty()) return 0;
        var white = Minecraft.getInstance().getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS)
                .apply(ResourceLocation.fromNamespaceAndPath("gadgetsngizmos", "block/hologram_white"));
        float textureU = (white.getU0() + white.getU1()) * .5F, textureV = (white.getV0() + white.getV1()) * .5F;
        var quads = SurfaceColorMesh.build(cells, 1 << (level - 1));
        for(var quad : quads){
            var face = Direction.values()[quad.face()];
            float shade = face == Direction.UP ? 1 : face == Direction.DOWN ? .65F : .85F;
            int color = quad.color();
            int red = Math.clamp((int)(((color >> 16 & 255) * .45F + 255 * .12F) * shade), 0, 255);
            int green = Math.clamp((int)(((color >> 8 & 255) * .7F + 255 * .2F) * shade), 0, 255);
            int blue = Math.clamp((int)(((color & 255) * .65F + 255 * .35F) * shade), 0, 255);
            for(int idx = 0; idx < 4; idx++){
                boolean reverse = face == Direction.UP || face == Direction.NORTH || face == Direction.EAST;
                int corner = reverse ? 3 - idx : idx;
                float u = quad.u() + (corner == 1 || corner == 2 ? quad.width() : 0);
                float v = quad.v() + (corner >= 2 ? quad.height() : 0);
                float x = face.getAxis() == Direction.Axis.X ? quad.plane() : u;
                float y = face.getAxis() == Direction.Axis.Y ? quad.plane() : v;
                float z = face.getAxis() == Direction.Axis.Y ? v : face.getAxis() == Direction.Axis.Z ? quad.plane() : u;
                builder.addVertex(x, y, z).setColor(red, green, blue, (int)(ALPHA * 255)).setUv(textureU, textureV)
                        .setLight(LightTexture.FULL_BRIGHT).setNormal(face.getStepX(), face.getStepY(), face.getStepZ());
            }
        }
        return quads.size();
    }

    // Apply biome colors before the common hologram tint
    private static void quads(VertexConsumer out, PoseStack poses, List<BakedQuad> quads,
                              HologramChunk.SurfaceBlock block, HologramChunk chunk){
        for(var quad : quads){
            int color = blockColor(quad, block, chunk);
            float shade = quad.getDirection() == Direction.UP ? 1 : quad.getDirection() == Direction.DOWN ? 0.65F : 0.85F;
            out.putBulkData(poses.last(), quad, ((color >> 16 & 255) / 255F * 0.45F + 0.12F) * shade,
                    ((color >> 8 & 255) / 255F * 0.7F + 0.2F) * shade,
                    ((color & 255) / 255F * 0.65F + 0.35F) * shade, ALPHA,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
    }

    static int blockColor(BakedQuad quad, HologramChunk.SurfaceBlock block, HologramChunk chunk){
        int color = 0xFFFFFF;
        if(quad.isTinted()){
            color = block.state().is(BlockTags.LEAVES) ? chunk.foliageColor() :
                    Minecraft.getInstance().getBlockColors().getColor(block.state(), null, null, quad.getTintIndex());
            if(color == -1 || block.state().is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)
                    || block.state().is(net.minecraft.world.level.block.Blocks.SUGAR_CANE) || block.state().is(BlockTags.REPLACEABLE_BY_TREES)) color = chunk.grassColor();
        }
        return color;
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

    private record ChunkMesh(HologramChunk source, VertexBuffer buffer, long builtAt, boolean dynamic,
                             Map<net.minecraft.core.BlockPos, ModelData> modelData, int level, int colorQuads){
        private ChunkMesh(HologramChunk source, VertexBuffer buffer, boolean liveModelData, Map<net.minecraft.core.BlockPos, ModelData> modelData, int level, int colorQuads){
            this(source, buffer, Minecraft.getInstance().level.getGameTime(), liveModelData
                    && source.blocks().stream().anyMatch(block -> block.state().hasBlockEntity()), Map.copyOf(modelData), level, colorQuads);
        }
        private boolean modelDataChanged(){
            if(modelData.isEmpty()) return false;
            var manager = Minecraft.getInstance().level.getModelDataManager();
            for(var entry : modelData.entrySet()) if(manager.getAt(entry.getKey()) != entry.getValue()) return true;
            return false;
        }
        private void close(){ if(buffer != null) buffer.close(); }
    }
}
