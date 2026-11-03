package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rieno.gadgetsandgizmos.lib.geometry.BandedFluidMesh;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

class ItemFluidPreviewTest{
    @Test
    void heldItemMotionSurvivesStackReplacementAndReloadsOnlyForNewModelsOrPlayers(){
        var model = (BakedModel)Proxy.newProxyInstance(BakedModel.class.getClassLoader(), new Class<?>[]{BakedModel.class},
                (proxy, method, args) -> { if(method.getName().equals("getQuads")) return List.of(); throw new UnsupportedOperationException(method.getName()); });
        var preview = new ItemFluidPreview<Object>(ResourceLocation.parse("test:liquid"), new AABB(0, 0, 0, 1, 1, 1), .85F);
        var player = new Object();
        var right = preview.entry(new Object(), ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, model, player);
        assertSame(right, preview.entry(new Object(), ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, model, player));
        assertNotSame(right, preview.entry(new Object(), ItemDisplayContext.FIRST_PERSON_LEFT_HAND, model, player));
        assertNotSame(right, preview.entry(new Object(), ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, model, new Object()));
        assertNotSame(preview.entry(new Object(), ItemDisplayContext.GUI, model, player),
                preview.entry(new Object(), ItemDisplayContext.GUI, model, player));
    }

    @Test
    void solidBandUsesOriginalRotatedTextureMappingAndEmissiveLight(){
        var uv = ItemFluidPreview.FaceUv.of(sourceFace());
        var face = new BandedFluidMesh.Face(List.of(new Vec3(0, .25D, 0), new Vec3(.75D, .25D, 0),
                new Vec3(.75D, .75D, 0), new Vec3(0, .75D, 0)), new Vec3(0, 0, -1), Direction.NORTH, 0xABCDEF);
        var pose = new PoseStack();
        pose.translate(2, 3, 4);
        var lit = new Sink();
        ItemFluidPreview.draw(pose.last(), lit, face, uv, true, 0, 7);
        assertEquals(4, lit.vertices.size());
        for(var vertex : lit.vertices){
            assertArrayEquals(new int[]{171, 205, 239, 255}, vertex.color);
            assertEquals(LightTexture.FULL_BRIGHT, vertex.light);
            assertEquals(7, vertex.overlay);
            assertArrayEquals(new float[]{0, 0, -1}, vertex.normal);
            float x = vertex.position[0] - 2, y = vertex.position[1] - 3;
            assertEquals(.25F + y * .5F, vertex.uv[0], 1.0E-6F);
            assertEquals(.75F - x * .25F, vertex.uv[1], 1.0E-6F);
        }
        var ordinary = new Sink();
        ItemFluidPreview.draw(pose.last(), ordinary, face, uv, false, 0x00300020, 7);
        for(var vertex : ordinary.vertices) assertEquals(0x00300020, vertex.light);
    }

    @Test
    void tiltedSurfacePolygonsEmitCompleteQuadsWithOneColor(){
        var face = new BandedFluidMesh.Face(List.of(new Vec3(0, 0, 0), new Vec3(0, .5D, 0),
                new Vec3(.5D, 1, 0), new Vec3(1, .5D, 0), new Vec3(1, 0, 0)), new Vec3(0, 0, -1), Direction.NORTH, 0xFF0000);
        var sink = new Sink();
        ItemFluidPreview.draw(new PoseStack().last(), sink, face, ItemFluidPreview.FaceUv.of(sourceFace()), true, 0, 0);
        assertEquals(12, sink.vertices.size());
        for(var vertex : sink.vertices) assertArrayEquals(new int[]{255, 0, 0, 255}, vertex.color);
    }

    private static BakedQuad sourceFace(){
        float[][] points = {{0, 0, 0}, {0, 1, 0}, {1, 1, 0}, {1, 0, 0}};
        int[] data = new int[32];
        for(int idx = 0; idx < 4; idx++){
            for(int axis = 0; axis < 3; axis++) data[idx * 8 + axis] = Float.floatToRawIntBits(points[idx][axis]);
            data[idx * 8 + 3] = 0xFFFFFFFF;
            data[idx * 8 + 4] = Float.floatToRawIntBits(.25F + points[idx][1] * .5F);
            data[idx * 8 + 5] = Float.floatToRawIntBits(.75F - points[idx][0] * .25F);
        }
        return new BakedQuad(data, -1, Direction.NORTH, null, true, true);
    }

    private static final class Sink implements VertexConsumer{
        private final List<Vertex> vertices = new ArrayList<>();
        private Vertex current;
        @Override public VertexConsumer addVertex(float x, float y, float z){
            current = new Vertex();
            current.position = new float[]{x, y, z};
            vertices.add(current);
            return this;
        }
        @Override public VertexConsumer setColor(int red, int green, int blue, int alpha){ current.color = new int[]{red, green, blue, alpha}; return this; }
        @Override public VertexConsumer setUv(float u, float v){ current.uv = new float[]{u, v}; return this; }
        @Override public VertexConsumer setUv1(int u, int v){ current.overlay = u | v << 16; return this; }
        @Override public VertexConsumer setUv2(int u, int v){ current.light = u | v << 16; return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z){ current.normal = new float[]{x, y, z}; return this; }
    }

    private static final class Vertex{
        private float[] position, uv, normal;
        private int[] color;
        private int light, overlay;
    }
}
