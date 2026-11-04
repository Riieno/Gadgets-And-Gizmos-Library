package com.rieno.gadgetsandgizmos.lib.client.render;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

// Clip projected GUI quads in their authored plane while preserving colors, texture UVs and lighting
public final class PlanarQuadClipBuffer implements MultiBufferSource, AutoCloseable{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                       CONSTANTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private final MultiBufferSource source;
    private final Matrix4f inverse;
    private final float width, height;
    private final Map<RenderType, Consumer> consumers = new IdentityHashMap<>();
    private Consumer active;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                       FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    public PlanarQuadClipBuffer(MultiBufferSource source, Matrix4fc canvasPose, int width, int height){
        this.source = source;
        this.inverse = new Matrix4f(canvasPose).invert();
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
    }

    // Finish pending vertices before the underlying source changes render type
    @Override public VertexConsumer getBuffer(RenderType type){
        if(active != null) active.flush();
        Consumer consumer = consumers.computeIfAbsent(type, ignored -> new Consumer());
        consumer.delegate = source.getBuffer(type);
        active = consumer;
        return consumer;
    }

    // Submit the last quad without ending or taking ownership of the caller's buffers
    @Override public void close(){
        if(active != null) active.flush();
        active = null;
    }

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        HELPERS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private final class Consumer implements VertexConsumer{
        private final Vertex[] vertices = {new Vertex(), new Vertex(), new Vertex(), new Vertex()};
        private final Vector3f local = new Vector3f();
        private VertexConsumer delegate;
        private Vertex current;
        private int count;

        @Override public VertexConsumer addVertex(float x, float y, float z){
            if(count == 4) flush();
            current = vertices[count++];
            current.x = x; current.y = y; current.z = z; current.flags = 0;
            inverse.transformPosition(x, y, z, local);
            current.px = local.x; current.py = local.y;
            return this;
        }

        @Override public VertexConsumer setColor(int red, int green, int blue, int alpha){
            current.red = red; current.green = green; current.blue = blue; current.alpha = alpha;
            current.flags |= 1;
            return this;
        }
        @Override public VertexConsumer setUv(float u, float v){ current.u = u; current.v = v; current.flags |= 2; return this; }
        @Override public VertexConsumer setUv1(int u, int v){ current.overlayU = u; current.overlayV = v; current.flags |= 4; return this; }
        @Override public VertexConsumer setUv2(int u, int v){ current.lightU = u; current.lightV = v; current.flags |= 8; return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z){
            current.nx = x; current.ny = y; current.nz = z; current.flags |= 16; return this;
        }

        // Most widget quads remain inside the canvas and need no clipping allocations
        private void flush(){
            if(count == 0) return;
            if(count != 4) throw new IllegalStateException("Planar clipping requires complete quads");
            int outside = 15, anyOutside = 0;
            for(Vertex vertex : vertices){
                int mask = (vertex.px < 0 ? 1 : 0) | (vertex.px > width ? 2 : 0)
                        | (vertex.py < 0 ? 4 : 0) | (vertex.py > height ? 8 : 0);
                outside &= mask; anyOutside |= mask;
            }
            if(anyOutside == 0){
                for(Vertex vertex : vertices) emit(vertex);
            }else if(outside == 0){
                List<Vertex> polygon = new ArrayList<>(List.of(vertices));
                for(int plane = 0; plane < 4 && !polygon.isEmpty(); plane++) polygon = clip(polygon, plane);
                if(polygon.size() == 4){
                    polygon.forEach(this::emit);
                }else for(int idx = 1; idx + 1 < polygon.size(); idx++){
                    emit(polygon.getFirst()); emit(polygon.get(idx)); emit(polygon.get(idx + 1)); emit(polygon.get(idx + 1));
                }
            }
            count = 0;
            current = null;
        }

        private List<Vertex> clip(List<Vertex> polygon, int plane){
            List<Vertex> clipped = new ArrayList<>(polygon.size() + 1);
            Vertex prev = polygon.getLast();
            float prevDistance = distance(prev, plane);
            for(Vertex next : polygon){
                float nextDistance = distance(next, plane);
                if((prevDistance >= 0) != (nextDistance >= 0)){
                    clipped.add(Vertex.mix(prev, next, prevDistance / (prevDistance - nextDistance)));
                }
                if(nextDistance >= 0) clipped.add(next);
                prev = next; prevDistance = nextDistance;
            }
            return clipped;
        }

        private float distance(Vertex vertex, int plane){
            return switch(plane){ case 0 -> vertex.px; case 1 -> width - vertex.px;
                case 2 -> vertex.py; default -> height - vertex.py; };
        }

        private void emit(Vertex vertex){
            delegate.addVertex(vertex.x, vertex.y, vertex.z);
            if((vertex.flags & 1) != 0) delegate.setColor(vertex.red, vertex.green, vertex.blue, vertex.alpha);
            if((vertex.flags & 2) != 0) delegate.setUv(vertex.u, vertex.v);
            if((vertex.flags & 4) != 0) delegate.setUv1(vertex.overlayU, vertex.overlayV);
            if((vertex.flags & 8) != 0) delegate.setUv2(vertex.lightU, vertex.lightV);
            if((vertex.flags & 16) != 0) delegate.setNormal(vertex.nx, vertex.ny, vertex.nz);
        }
    }

    private static final class Vertex{
        private float x, y, z, px, py, u, v, nx, ny, nz;
        private int red, green, blue, alpha, overlayU, overlayV, lightU, lightV, flags;

        // Intersections retain the interpolants that the GPU would have used along the original edge
        private static Vertex mix(Vertex a, Vertex b, float amount){
            Vertex res = new Vertex();
            res.x = blend(a.x, b.x, amount); res.y = blend(a.y, b.y, amount); res.z = blend(a.z, b.z, amount);
            res.px = blend(a.px, b.px, amount); res.py = blend(a.py, b.py, amount);
            res.u = blend(a.u, b.u, amount); res.v = blend(a.v, b.v, amount);
            res.nx = blend(a.nx, b.nx, amount); res.ny = blend(a.ny, b.ny, amount); res.nz = blend(a.nz, b.nz, amount);
            res.red = Math.round(blend(a.red, b.red, amount)); res.green = Math.round(blend(a.green, b.green, amount));
            res.blue = Math.round(blend(a.blue, b.blue, amount)); res.alpha = Math.round(blend(a.alpha, b.alpha, amount));
            res.overlayU = Math.round(blend(a.overlayU, b.overlayU, amount)); res.overlayV = Math.round(blend(a.overlayV, b.overlayV, amount));
            res.lightU = Math.round(blend(a.lightU, b.lightU, amount)); res.lightV = Math.round(blend(a.lightV, b.lightV, amount));
            res.flags = a.flags | b.flags;
            return res;
        }

        private static float blend(float a, float b, float amount){ return a + (b - a) * amount; }
    }
}
