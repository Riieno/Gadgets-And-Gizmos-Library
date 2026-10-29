package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HologramTintTest{
    @Test
    void packedEntityWritesKeepTheirOriginalLightAndOverlay() throws ReflectiveOperationException{
        var type = Class.forName(HologramEntityRenderer.class.getName() + "$Tint");
        var constructor = type.getDeclaredConstructor(VertexConsumer.class); constructor.setAccessible(true);
        var fluent = new Sink();
        var packed = new Sink();
        var original = (VertexConsumer)constructor.newInstance(fluent);
        var optimized = (VertexConsumer)constructor.newInstance(packed);
        original.addVertex(1, 2, 3).setColor(101, 199, 21, 127).setUv(.25F, .75F)
                .setUv1(15, 65535).setUv2(5, 65535).setNormal(.25F, -.5F, .75F);
        optimized.addVertex(1, 2, 3, 127 << 24 | 101 << 16 | 199 << 8 | 21,
                .25F, .75F, 65535 << 16 | 15, 65535 << 16 | 5, .25F, -.5F, .75F);
        assertEquals(1, packed.bulkWrites);
        assertArrayEquals(fluent.geometry, packed.geometry);
        assertArrayEquals(fluent.channels, packed.channels);
    }

    @Test
    void packedWritesMatchFluentTintForEveryAlphaValue(){
        for(int alpha = 0; alpha <= 255; alpha++){
            var fluent = new Sink();
            var packed = new Sink();
            new HologramBlockEntityRenderer.Tint(fluent).addVertex(1, 2, 3)
                    .setColor(101, 199, 21, alpha).setUv(.25F, .75F)
                    .setUv1(65535, 15).setUv2(0, 5).setNormal(.25F, -.5F, .75F);
            new HologramBlockEntityRenderer.Tint(packed).addVertex(1, 2, 3,
                    alpha << 24 | 101 << 16 | 199 << 8 | 21, .25F, .75F,
                    15 << 16 | 65535, 5 << 16, .25F, -.5F, .75F);
            assertEquals(1, packed.bulkWrites);
            assertArrayEquals(fluent.geometry, packed.geometry);
            assertArrayEquals(fluent.channels, packed.channels);
        }
    }

    @Test
    void vertexWritesKeepTheirTintAlphaLightingAndFluentWrapper(){
        var sink = new Sink();
        var tint = new HologramBlockEntityRenderer.Tint(sink);
        assertSame(tint, tint.addVertex(1, 2, 3));
        assertSame(tint, tint.setColor(100, 200, 20, 128));
        assertSame(tint, tint.setUv(.25F, .75F));
        assertSame(tint, tint.setUv1(7, 8));
        assertSame(tint, tint.setUv2(0, 5));
        assertSame(tint, tint.setNormal(0, 1, 0));
        assertArrayEquals(new float[]{1, 2, 3, .25F, .75F, 0, 1, 0}, sink.geometry);
        assertArrayEquals(new int[]{75, 185, 130, 96, 7, 8, 240, 240}, sink.channels);
    }

    private static final class Sink implements VertexConsumer{
        private int bulkWrites;
        private final float[] geometry = new float[8];
        private final int[] channels = new int[8];
        @Override public void addVertex(float x, float y, float z, int color, float u, float v,
                                        int overlay, int light, float normalX, float normalY, float normalZ){
            bulkWrites++;
            VertexConsumer.super.addVertex(x, y, z, color, u, v, overlay, light, normalX, normalY, normalZ);
        }
        @Override public VertexConsumer addVertex(float x, float y, float z){ geometry[0] = x; geometry[1] = y; geometry[2] = z; return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a){ channels[0] = r; channels[1] = g; channels[2] = b; channels[3] = a; return this; }
        @Override public VertexConsumer setUv(float u, float v){ geometry[3] = u; geometry[4] = v; return this; }
        @Override public VertexConsumer setUv1(int u, int v){ channels[4] = u; channels[5] = v; return this; }
        @Override public VertexConsumer setUv2(int u, int v){ channels[6] = u; channels[7] = v; return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z){ geometry[5] = x; geometry[6] = y; geometry[7] = z; return this; }
    }
}
