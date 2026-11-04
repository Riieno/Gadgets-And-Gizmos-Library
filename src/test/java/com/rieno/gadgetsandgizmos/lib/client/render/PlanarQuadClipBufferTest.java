package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Crop projected geometry without shifting image UVs, text colors or light
class PlanarQuadClipBufferTest{
    @Test void clippingInAnOrientedPlanePreservesInterpolatedTextureCoordinates(){
        var pose = new Matrix4f().translation(20, 10, 7).rotateY(.4F).rotateZ(.6F).scale(2, .7F, 1);
        var output = new Capture();
        try(var clip = new PlanarQuadClipBuffer(type -> output, pose, 10, 8)){
            var vertices = clip.getBuffer(null);
            quad(vertices, pose, -5, -4, 15, 16);
        }
        assertEquals(4, output.vertices.size());
        var inverse = new Matrix4f(pose).invert();
        for(var vertex : output.vertices){
            var local = inverse.transformPosition(vertex.x, vertex.y, vertex.z, new Vector3f());
            assertTrue(local.x >= -1e-4 && local.x <= 10 + 1e-4);
            assertTrue(local.y >= -1e-4 && local.y <= 8 + 1e-4);
            assertEquals((local.x + 5) / 20, vertex.u, 1e-4);
            assertEquals((local.y + 4) / 20, vertex.v, 1e-4);
            assertEquals(0x80ABCDEF, vertex.color);
            assertEquals(240, vertex.lightU);
            assertEquals(240, vertex.lightV);
        }
    }

    @Test void insideQuadsRemainUnchangedAndHiddenQuadsAreDiscarded(){
        var output = new Capture();
        try(var clip = new PlanarQuadClipBuffer(type -> output, new Matrix4f(), 20, 20)){
            quad(clip.getBuffer(null), new Matrix4f(), 2, 3, 12, 13);
            quad(clip.getBuffer(null), new Matrix4f(), 21, 3, 31, 13);
        }
        assertEquals(4, output.vertices.size());
        assertEquals(2, output.vertices.getFirst().x);
        assertEquals(3, output.vertices.getFirst().y);
        assertEquals(0, output.vertices.getFirst().u);
        assertEquals(0, output.vertices.getFirst().v);
    }

    private static void quad(VertexConsumer vertices, Matrix4f pose, float left, float top, float right, float bottom){
        vertices.addVertex(pose, left, top, 0).setColor(0x80ABCDEF).setUv(0, 0).setLight(0xF000F0);
        vertices.addVertex(pose, left, bottom, 0).setColor(0x80ABCDEF).setUv(0, 1).setLight(0xF000F0);
        vertices.addVertex(pose, right, bottom, 0).setColor(0x80ABCDEF).setUv(1, 1).setLight(0xF000F0);
        vertices.addVertex(pose, right, top, 0).setColor(0x80ABCDEF).setUv(1, 0).setLight(0xF000F0);
    }

    private static final class Vertex{
        private float x, y, z, u, v;
        private int color, lightU, lightV;
    }

    private static final class Capture implements VertexConsumer{
        private final List<Vertex> vertices = new ArrayList<>();
        private Vertex current;
        @Override public VertexConsumer addVertex(float x, float y, float z){
            current = new Vertex(); current.x = x; current.y = y; current.z = z; vertices.add(current); return this;
        }
        @Override public VertexConsumer setColor(int red, int green, int blue, int alpha){
            current.color = alpha << 24 | red << 16 | green << 8 | blue; return this;
        }
        @Override public VertexConsumer setUv(float u, float v){ current.u = u; current.v = v; return this; }
        @Override public VertexConsumer setUv1(int u, int v){ return this; }
        @Override public VertexConsumer setUv2(int u, int v){ current.lightU = u; current.lightV = v; return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z){ return this; }
    }
}
