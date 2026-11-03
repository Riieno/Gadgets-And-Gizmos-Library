package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rieno.gadgetsandgizmos.lib.color.DensityColorPalette;
import com.rieno.gadgetsandgizmos.lib.geometry.BandedFluidMesh;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Locale;

// Measure CPU preparation and vertex submission without making a GPU frame-rate claim
public final class FluidPreviewBenchmark{
    private static volatile double checksum;

    public static void main(String[] args){
        var layers = new ArrayList<DensityColorPalette.Layer>();
        for(int idx = 0; idx < 16; idx++) layers.add(new DensityColorPalette.Layer(idx * 0x10101, 128, idx));
        var palette = new DensityColorPalette(layers);
        var colors = palette.gradient();
        var bounds = new AABB(0, 0, 0, .175D, .175D, .425D);
        var textures = FluidPreviewMeshTest.textures();
        FluidPreviewMesh mesh = null;
        var field = new DensityFluidField(bounds, .85F, palette);
        field.update(new DensityFluidMotion.Frame(0, 1, 0, 0, 0, 0), 0);
        var motionFrame = new DensityFluidMotion.Frame(0, 1, 0, .7F, -.5F, .8F);
        long start = 0;
        int count = 1000, maxVertices = 0;
        for(int idx = 1; idx <= count + 200; idx++){
            if(idx == 201) start = System.nanoTime();
            var up = new Vector3f((float)Math.sin(idx * .01D), .8F, .35F).normalize();
            motionFrame = new DensityFluidMotion.Frame(up.x, up.y, up.z, .7F, -.5F, .8F);
            field.update(motionFrame, idx);
            mesh = FluidPreviewMesh.build(BandedFluidMesh.build(bounds, up, .85F, colors), textures, bounds, palette, field, mesh);
            maxVertices = Math.max(maxVertices, mesh.vertices());
        }
        double buildMicros = (System.nanoTime() - start) / (count * 1000D);
        var sink = new Sink();
        var pose = new PoseStack().last();
        var frame = new DensityFluidMotion.Frame(0, 1, 0, .5F, -.3F, .6F);
        count = 10000;
        for(int idx = -2000; idx < count; idx++){
            if(idx == 0) start = System.nanoTime();
            mesh.draw(pose, sink, frame, .5F, true, 0, 0);
        }
        double drawMicros = (System.nanoTime() - start) / (count * 1000D);
        checksum = sink.total;
        System.out.printf(Locale.ROOT, "Fluid preview: field and mesh refresh %.2f us; cached vertex submission %.2f us; max %d vertices%n", buildMicros, drawMicros, maxVertices);
        var motion = new DensityFluidMotion();
        var up = new Vector3f(0, 1, 0);
        var pos = new Vector3f();
        motion.update(up, pos, 0);
        start = System.nanoTime();
        for(int idx = 1; idx <= 100000; idx++) checksum = motion.update(up, pos, idx / 3D).mixing();
        System.out.printf(Locale.ROOT, "Settled motion: %.3f us per presentation; %d simulation steps%n", (System.nanoTime() - start) / 100000000D, motion.steps());
    }

    private static final class Sink implements VertexConsumer{
        private double total;
        @Override public VertexConsumer addVertex(float x, float y, float z){ total += x + y + z; return this; }
        @Override public VertexConsumer setColor(int red, int green, int blue, int alpha){ total += red + green + blue + alpha; return this; }
        @Override public VertexConsumer setUv(float u, float v){ total += u + v; return this; }
        @Override public VertexConsumer setUv1(int u, int v){ return this; }
        @Override public VertexConsumer setUv2(int u, int v){ return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z){ return this; }
    }
}
