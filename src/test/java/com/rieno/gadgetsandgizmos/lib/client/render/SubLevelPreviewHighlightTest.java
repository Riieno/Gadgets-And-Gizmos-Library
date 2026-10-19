package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SubLevelPreviewHighlightTest{
    private static final UUID BODY = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeAll static void bootstrap(){
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        net.neoforged.fml.ModList.of(List.of(), List.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void allFacesAndWholeBlockOutlinesSurviveSharedBufferSwitchesAndRepeatedFrames() throws Exception{
        SubLevelPreviewRenderer preview = preview();
        List<SubLevelPreviewRenderer.Highlight> highlights = new ArrayList<>();
        for(Direction face : Direction.values()){
            highlights.add(new SubLevelPreviewRenderer.Highlight(BODY, BlockPos.ZERO, face, 0xFF33AAFF));
        }
        highlights.add(new SubLevelPreviewRenderer.Highlight(BODY, BlockPos.ZERO, 0xFFAA7733));
        highlights.add(null);
        highlights.add(new SubLevelPreviewRenderer.Highlight(null, BlockPos.ZERO, Direction.UP, -1));
        highlights.add(new SubLevelPreviewRenderer.Highlight(BODY, new BlockPos(8, 0, 0), Direction.UP, -1));
        PoseStack pose = new PoseStack();
        pose.translate(4, 5, 6);
        Matrix4f parent = new Matrix4f(pose.last().pose());
        try(CapturingBuffers buffers = new CapturingBuffers()){
            for(int frame = 0; frame < 2; frame++){
                buffers.vertices.clear();
                render(preview, highlights, pose, buffers);
                assertEquals(48, buffers.vertices.getOrDefault(VertexFormat.Mode.QUADS, 0));
                assertEquals(336, buffers.vertices.getOrDefault(VertexFormat.Mode.LINES, 0));
                assertEquals(parent, pose.last().pose());
                assertTrue(buffers.startedBuildersEmpty());
            }
        }
    }

    @Test void wholeBlockAndHiddenHighlightsDoNotEmitFaceGeometry() throws Exception{
        SubLevelPreviewRenderer preview = preview();
        try(CapturingBuffers buffers = new CapturingBuffers()){
            render(preview, List.of(new SubLevelPreviewRenderer.Highlight(BODY, BlockPos.ZERO, -1)), new PoseStack(), buffers);
            assertEquals(0, buffers.vertices.getOrDefault(VertexFormat.Mode.QUADS, 0));
            assertEquals(48, buffers.vertices.getOrDefault(VertexFormat.Mode.LINES, 0));
            buffers.vertices.clear();
            preview.setVisibilityPredicate(block -> false);
            render(preview, List.of(new SubLevelPreviewRenderer.Highlight(BODY, BlockPos.ZERO, Direction.UP, -1)), new PoseStack(), buffers);
            render(preview, List.of(), new PoseStack(), buffers);
            render(preview, null, new PoseStack(), buffers);
            assertTrue(buffers.vertices.isEmpty());
            assertTrue(buffers.startedBuildersEmpty());
        }
    }

    private static SubLevelPreviewRenderer preview() throws Exception{
        SubLevelPreviewRenderer preview = new SubLevelPreviewRenderer(ResourceLocation.fromNamespaceAndPath("gadgetsngizmos", "test/preview"));
        preview.setSnapshotBlocks(List.of(new SubLevelPreviewRenderer.SnapshotBlock(
                BODY, BlockPos.ZERO, Blocks.STONE.defaultBlockState(), Vec3.ZERO)));
        var refresh = SubLevelPreviewRenderer.class.getDeclaredMethod("refreshBlocks", ClientLevel.class, float.class);
        refresh.setAccessible(true);
        assertEquals(true, refresh.invoke(preview, null, 0F));
        return preview;
    }

    private static void render(SubLevelPreviewRenderer preview, Collection<SubLevelPreviewRenderer.Highlight> highlights,
                               PoseStack pose, MultiBufferSource.BufferSource buffers) throws Exception{
        var render = SubLevelPreviewRenderer.class.getDeclaredMethod("renderHighlights", ClientLevel.class,
                ClientSubLevel.class, float.class, Collection.class, PoseStack.class, MultiBufferSource.BufferSource.class);
        render.setAccessible(true);
        render.invoke(preview, null, null, 0F, highlights, pose, buffers);
    }

    // Use Minecraft's real shared-buffer lifecycle and vertex validation without uploading to a GPU
    private static final class CapturingBuffers extends MultiBufferSource.BufferSource implements AutoCloseable{
        private final Map<VertexFormat.Mode, Integer> vertices = new EnumMap<>(VertexFormat.Mode.class);

        private CapturingBuffers(){
            super(new ByteBufferBuilder(4096), new LinkedHashMap<>());
        }

        @Override public void endBatch(RenderType type){
            BufferBuilder builder = startedBuilders.remove(type);
            if(builder != null){
                try(MeshData mesh = builder.build()){
                    if(mesh != null) vertices.merge(type.mode(), mesh.drawState().vertexCount(), Integer::sum);
                }
            }
            if(type.equals(lastSharedType)) lastSharedType = null;
        }

        private boolean startedBuildersEmpty(){
            return startedBuilders.isEmpty();
        }

        @Override public void close(){
            endBatch();
            sharedBuffer.close();
        }
    }
}
