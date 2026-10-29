package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

// Retain local vertices without flattening depth layers or changing the original render types
public final class RenderGeometryCache{
    private final Map<RenderType, Vertices> batches = new LinkedHashMap<>();

    public void clear(){ batches.clear(); }

    // The callback supplies local geometry; the caller's current pose is applied on every presentation
    public void render(PoseStack poses, MultiBufferSource buffers, boolean refresh, Consumer<MultiBufferSource> draw){
        if(refresh){
            batches.values().forEach(vertices -> vertices.count = 0);
            draw.accept(type -> batches.computeIfAbsent(type, ignored -> new Vertices()));
            batches.values().removeIf(vertices -> vertices.count == 0);
        }
        batches.forEach((type, vertices) -> vertices.draw(poses,
                buffers.getBuffer(refresh ? type : RenderSurfaceVisibility.untracked(type))));
    }

    private static final class Vertices implements VertexConsumer{
        private static final int STRIDE = 14;
        private float[] data = new float[STRIDE * 64];
        private int count;
        private int idx;

        @Override public VertexConsumer addVertex(float x, float y, float z){
            idx = count++ * STRIDE;
            if(idx + STRIDE > data.length) data = Arrays.copyOf(data, data.length * 2);
            Arrays.fill(data, idx, idx + STRIDE, 0F);
            data[idx] = x; data[idx + 1] = y; data[idx + 2] = z;
            data[idx + 3] = Float.intBitsToFloat(-1);
            return this;
        }
        @Override public VertexConsumer setColor(int r, int g, int b, int a){
            data[idx + 3] = Float.intBitsToFloat(a << 24 | r << 16 | g << 8 | b); return this;
        }
        @Override public VertexConsumer setUv(float u, float v){ data[idx + 4] = u; data[idx + 5] = v; return this; }
        @Override public VertexConsumer setUv1(int u, int v){ data[idx + 6] = u; data[idx + 7] = v; return this; }
        @Override public VertexConsumer setUv2(int u, int v){ data[idx + 8] = u; data[idx + 9] = v; return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z){
            data[idx + 10] = x; data[idx + 11] = y; data[idx + 12] = z; return this;
        }

        private void draw(PoseStack poses, VertexConsumer target){
            var pose = poses.last();
            var pos = new Vector3f();
            var normal = new Vector3f();
            for(int i = 0; i < count * STRIDE; i += STRIDE){
                pose.pose().transformPosition(data[i], data[i + 1], data[i + 2], pos);
                pose.transformNormal(data[i + 10], data[i + 11], data[i + 12], normal);
                int overlayU = (int)data[i + 6], overlayV = (int)data[i + 7];
                int lightU = (int)data[i + 8], lightV = (int)data[i + 9];
                if(((overlayU | overlayV | lightU | lightV) & ~0xFFFF) != 0){
                    target.addVertex(pos.x, pos.y, pos.z).setColor(Float.floatToRawIntBits(data[i + 3]))
                            .setUv(data[i + 4], data[i + 5]).setUv1(overlayU, overlayV).setUv2(lightU, lightV)
                            .setNormal(normal.x, normal.y, normal.z);
                    continue;
                }
                int overlay = overlayU | overlayV << 16;
                int light = lightU | lightV << 16;
                target.addVertex(pos.x, pos.y, pos.z, Float.floatToRawIntBits(data[i + 3]),
                        data[i + 4], data[i + 5], overlay, light, normal.x, normal.y, normal.z);
            }
        }
    }
}
