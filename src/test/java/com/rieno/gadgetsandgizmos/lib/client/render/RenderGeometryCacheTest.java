package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RenderGeometryCacheTest{
    private static final int[] COLORS = {0x01234567, 0x7FABCDEF, 0xFF102030, 0x00F0FF01};

    @Test
    void packedReplayPreservesTransformedNormalsAndEveryVertexAttribute(){
        var sink = new PackedSink();
        compare(sink);
        assertEquals(8, sink.bulkWrites);
    }

    @Test
    void consumersWithoutPackedSupportKeepTheirOriginalAttributes(){ compare(new Sink()); }

    @Test
    void customConsumersKeepSignedAndWideUvCoordinates(){
        var sink = new PackedSink();
        new RenderGeometryCache().render(new PoseStack(), ignored -> sink, true, buffers ->
                buffers.getBuffer(null).addVertex(1, 2, 3).setUv1(-1, 65536).setUv2(70000, -16));
        assertEquals(0, sink.bulkWrites);
        var vertex = ((Sink)sink).vertices.getFirst();
        assertEquals(-1, vertex[6]); assertEquals(65536, vertex[7]);
        assertEquals(70000, vertex[8]); assertEquals(-16, vertex[9]);
    }

    private static void compare(Sink actual){
        var poses = new PoseStack();
        poses.translate(10.25D, -4.75D, 2.5D);
        poses.mulPose(new Quaternionf().rotationXYZ(.25F, -.6F, .15F));
        poses.scale(.5F, 2F, 3F);
        var expected = new Sink();
        var cache = new RenderGeometryCache();
        for(int pass = 0; pass < 2; pass++){
            if(pass == 1) poses.translate(7.5D, 3D, -2D);
            for(int idx = 0; idx < 4; idx++){
                expected.addVertex(poses.last().pose(), idx - .25F, idx + 1.5F, 3.75F - idx)
                        .setColor(COLORS[idx]).setUv(.125F * idx, .75F)
                        .setUv1(idx == 0 ? 65535 : idx, 15).setUv2(240, idx == 0 ? 65535 : idx)
                        .setNormal(poses.last(), .3F, .8F, -.1F);
            }
            cache.render(poses, ignored -> actual, pass == 0, buffers -> {
                var target = buffers.getBuffer(null);
                for(int idx = 0; idx < 4; idx++){
                    target.addVertex(idx - .25F, idx + 1.5F, 3.75F - idx)
                            .setColor(COLORS[idx]).setUv(.125F * idx, .75F)
                            .setUv1(idx == 0 ? 65535 : idx, 15).setUv2(240, idx == 0 ? 65535 : idx)
                            .setNormal(.3F, .8F, -.1F);
                }
            });
        }
        assertEquals(expected.vertices.size(), actual.vertices.size());
        for(int idx = 0; idx < expected.vertices.size(); idx++) assertArrayEquals(expected.vertices.get(idx), actual.vertices.get(idx));
    }

    private static class Sink implements VertexConsumer{
        private final List<int[]> vertices = new ArrayList<>();
        private int[] vertex;
        @Override public VertexConsumer addVertex(float x, float y, float z){
            vertex = new int[13]; vertices.add(vertex);
            vertex[0] = Float.floatToRawIntBits(x); vertex[1] = Float.floatToRawIntBits(y); vertex[2] = Float.floatToRawIntBits(z); return this;
        }
        @Override public VertexConsumer setColor(int r, int g, int b, int a){ vertex[3] = a << 24 | r << 16 | g << 8 | b; return this; }
        @Override public VertexConsumer setUv(float u, float v){ vertex[4] = Float.floatToRawIntBits(u); vertex[5] = Float.floatToRawIntBits(v); return this; }
        @Override public VertexConsumer setUv1(int u, int v){ vertex[6] = u; vertex[7] = v; return this; }
        @Override public VertexConsumer setUv2(int u, int v){ vertex[8] = u; vertex[9] = v; return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z){ vertex[10] = Float.floatToRawIntBits(x); vertex[11] = Float.floatToRawIntBits(y); vertex[12] = Float.floatToRawIntBits(z); return this; }
    }
    private static final class PackedSink extends Sink{
        private int bulkWrites;
        @Override public void addVertex(float x, float y, float z, int color, float u, float v,
                                        int overlay, int light, float normalX, float normalY, float normalZ){
            bulkWrites++;
            addVertex(x, y, z).setColor(color).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(normalX, normalY, normalZ);
        }
    }
}
