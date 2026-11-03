package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rieno.gadgetsandgizmos.lib.color.DensityColorPalette;
import com.rieno.gadgetsandgizmos.lib.geometry.BandedFluidMesh;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FluidPreviewMeshTest{
    private static final AABB BOUNDS = new AABB(0, 0, 0, 1, 1, 1);
    private static final DensityColorPalette PALETTE = new DensityColorPalette(List.of(
            new DensityColorPalette.Layer(0xFF0000, 3, 0), new DensityColorPalette.Layer(0x0000FF, 1, 15)));

    @Test
    void movingColoursEaseToExactRestingBandsWithoutReplacingTheSurface(){
        var field = new DensityFluidField(BOUNDS, .85F, PALETTE);
        var rest = new DensityFluidMotion.Frame(0, 1, 0, 0, 0, 0);
        field.update(rest, 0);
        for(int tick = 1; tick <= 60; tick++) field.update(new DensityFluidMotion.Frame(0, 1, 0, .9F, .7F, .95F), tick);
        var faces = BandedFluidMesh.build(BOUNDS, rest.up(), .85F, PALETTE.gradient());
        var mesh = FluidPreviewMesh.build(faces, textures(), BOUNDS, PALETTE, field, null);
        var moving = new Sink();
        var settled = new Sink();
        mesh.draw(new PoseStack().last(), moving, rest, 1, 1, 0, true, 0, 0);
        mesh.draw(new PoseStack().last(), settled, rest, 1, 1, 1, true, 0, 0);
        assertEquals(moving.points, settled.points, "The transition must retain the same clipped faces");
        int changed = 0;
        for(float progress : new float[]{.25F, .5F, .75F, .99F}){
            var easing = new Sink();
            mesh.draw(new PoseStack().last(), easing, rest, 1, 1, progress, true, 0, 0);
            for(int idx = 0; idx < moving.colors.size(); idx++){
                int from = moving.colors.get(idx), to = settled.colors.get(idx), color = easing.colors.get(idx);
                assertTrue(to == 0xFFFF0000 || to == 0xFF0000FF, "Rest must use the exact source band colours");
                if(progress == .5F && color != from && color != to) changed++;
                for(int shift : new int[]{0, 8, 16}){
                    int error = Math.abs((color >> shift & 255) - (to >> shift & 255));
                    int original = Math.abs((from >> shift & 255) - (to >> shift & 255));
                    assertTrue(error <= original);
                    if(progress == .99F) assertTrue(error <= 1, "Discarding the moving mesh must not change its final colours");
                }
                assertEquals(LightTexture.FULL_BRIGHT, easing.lights.get(idx));
            }
        }
        assertTrue(changed > 50, "The return must visibly animate through intermediate colours");
    }

    @Test
    void fractionalFramesKeepClippedFacesOnTheBottleAndFreeSurface(){
        var volume = new com.rieno.gadgetsandgizmos.lib.color.WeightedColorGradient(List.of(
                new com.rieno.gadgetsandgizmos.lib.color.WeightedColorGradient.Color(0xFFFFFF, 1)));
        FluidPreviewMesh prev = null;
        for(int tick = 0; tick < 35; tick++){
            var up = new Vector3f((float)Math.sin(tick * .31D), .8F, (float)Math.cos(tick * .22D)).normalize();
            var faces = BandedFluidMesh.build(BOUNDS, up, .85F, volume);
            var mesh = FluidPreviewMesh.build(faces, textures(), BOUNDS, PALETTE, prev);
            double level = new BandedFluidMesh.Projection(BOUNDS, up).height(.85F);
            for(float alpha : new float[]{0, .25F, .5F, .75F, 1}){
                var sink = new Sink();
                mesh.draw(new PoseStack().last(), sink, new DensityFluidMotion.Frame(up.x, up.y, up.z, 0, 0, 0), alpha, false, 0, 0);
                for(var p : sink.points){
                    double projection = p.x * up.x + p.y * up.y + p.z * up.z;
                    boolean wall = p.x == 0 || p.x == 1 || p.y == 0 || p.y == 1 || p.z == 0 || p.z == 1;
                    assertTrue(wall || Math.abs(projection - level) < 1.0E-6D, "Frame interpolation must not fold a face into the tank");
                    assertTrue(projection <= level + 1.0E-6D, "Frame interpolation must not leave faces above the free surface");
                }
            }
            prev = mesh;
        }
    }

    @Test
    void continuousVolumeMeshCarriesDyeAcrossBandBoundariesAndKeepsEmission(){
        var palette = new DensityColorPalette(List.of(new DensityColorPalette.Layer(0xFF0000, 1, 0), new DensityColorPalette.Layer(0x0000FF, 1, 15)));
        var field = new DensityFluidField(BOUNDS, .85F, palette);
        var rest = new DensityFluidMotion.Frame(0, 1, 0, 0, 0, 0);
        field.update(rest, 0);
        var volume = new com.rieno.gadgetsandgizmos.lib.color.WeightedColorGradient(List.of(
                new com.rieno.gadgetsandgizmos.lib.color.WeightedColorGradient.Color(0xFFFFFF, 1)));
        var surface = BandedFluidMesh.build(BOUNDS, rest.up(), .85F, volume);
        var mesh = FluidPreviewMesh.build(surface, textures(), BOUNDS, palette, field, null);
        var before = new Sink();
        mesh.draw(new PoseStack().last(), before, rest, 1, 1, true, 0, 0);
        var moving = new DensityFluidMotion.Frame(0, 1, 0, .9F, .65F, .9F);
        for(int tick = 1; tick <= 55; tick++) field.update(moving, tick);
        mesh.refreshColors();
        var mixed = new Sink();
        mesh.draw(new PoseStack().last(), mixed, moving, 1, 1, true, 0, 0);
        int transported = 0;
        for(int idx = 0; idx < mixed.points.size(); idx++){
            if(before.points.get(idx).y < .3D && (mixed.colors.get(idx) >> 16 & 255) - (before.colors.get(idx) >> 16 & 255) > 30) transported++;
            assertEquals(LightTexture.FULL_BRIGHT, mixed.lights.get(idx));
            var p = mixed.points.get(idx);
            assertTrue(p.x >= 0 && p.x <= 1 && p.y >= 0 && p.y <= 1 && p.z >= 0 && p.z <= 1);
            var original = before.points.get(idx);
            if(original.x == 0 || original.x == 1 || original.y == 0 || original.y == 1 || original.z == 0 || original.z == 1){
                assertEquals(original, p, "Wave motion must not open cracks where the surface meets a bottle wall");
            }
        }
        assertTrue(transported > 10, "The rendered volume must show red dye transported below its resting band");
        assertTrue(mesh.vertices() <= 2048);
        var transitioning = new Sink();
        mesh.draw(new PoseStack().last(), transitioning, moving, .5F, .5F, true, 0, 0);
        var sharedColors = new java.util.HashMap<Vec3, Integer>();
        var sharedTransitions = new java.util.HashMap<Vec3, Integer>();
        for(int idx = 0; idx < mixed.colors.size(); idx++){
            int color = mixed.colors.get(idx), transition = transitioning.colors.get(idx);
            assertEquals(0, color >> 8 & 255);
            assertEquals(0xFF, color >>> 24);
            var old = sharedColors.putIfAbsent(mixed.points.get(idx), color);
            if(old != null) assertEquals(old.intValue(), color, "Shared edges must not have per-primitive colour discontinuities");
            var prev = sharedTransitions.putIfAbsent(transitioning.points.get(idx), transition);
            if(prev != null) assertEquals(prev.intValue(), transition, "Colour transitions must agree at shared vertices");
        }
    }

    @Test
    void wavesRemainInsideTheBottleAndBlendingDisappearsWhenSettled(){
        var src = BandedFluidMesh.build(BOUNDS, new Vector3f(0, 1, 0), .85F, PALETTE.gradient());
        var mesh = FluidPreviewMesh.build(src, textures(), BOUNDS, PALETTE, null);
        var still = new Sink();
        mesh.draw(new PoseStack().last(), still, new DensityFluidMotion.Frame(0, 1, 0, 0, 0, 0), 1, false, 0x30, 0);
        var shaken = new Sink();
        mesh.draw(new PoseStack().last(), shaken, new DensityFluidMotion.Frame(0, 1, 0, 1, -.8F, .9F), .5F, true, 0, 0);
        assertEquals(still.points.size(), shaken.points.size());
        boolean moved = false, blended = false;
        for(int idx = 0; idx < shaken.points.size(); idx++){
            var point = shaken.points.get(idx);
            assertTrue(point.x >= 0 && point.x <= 1 && point.y >= 0 && point.y <= 1 && point.z >= 0 && point.z <= 1);
            moved |= point.distanceToSqr(still.points.get(idx)) > 1.0E-8D;
            blended |= shaken.colors.get(idx) != 0xFF0000FF && shaken.colors.get(idx) != 0xFFFF0000;
            assertEquals(LightTexture.FULL_BRIGHT, shaken.lights.get(idx));
            assertTrue(still.colors.get(idx) == 0xFF0000FF || still.colors.get(idx) == 0xFFFF0000);
            assertEquals(0x30, still.lights.get(idx));
        }
        assertTrue(moved);
        assertTrue(blended);
    }

    @Test
    void sixteenLayerMeshesKeepAFixedUpperBoundAcrossOrientations(){
        var layers = new ArrayList<DensityColorPalette.Layer>();
        for(int idx = 0; idx < 16; idx++) layers.add(new DensityColorPalette.Layer(idx * 0x10101, idx % 2 == 0 ? 1 : 128, idx));
        var palette = new DensityColorPalette(layers);
        FluidPreviewMesh prev = null;
        for(int idx = 0; idx < 60; idx++){
            var up = new Vector3f((float)Math.sin(idx * .2D), (float)Math.cos(idx * .2D), .35F).normalize();
            var faces = BandedFluidMesh.build(BOUNDS, up, .85F, palette.gradient());
            var mesh = FluidPreviewMesh.build(faces, textures(), BOUNDS, palette, prev);
            assertTrue(mesh.vertices() > 0 && mesh.vertices() <= 4096);
            assertEquals(0, mesh.vertices() % 4);
            var sink = new Sink();
            mesh.draw(new PoseStack().last(), sink, new DensityFluidMotion.Frame(up.x, up.y, up.z, .7F, -.6F, .8F), .5F, false, 0, 0);
            for(var point : sink.points){
                assertTrue(Double.isFinite(point.x + point.y + point.z));
                assertTrue(point.x >= 0 && point.x <= 1 && point.y >= 0 && point.y <= 1 && point.z >= 0 && point.z <= 1);
            }
            prev = mesh;
        }
    }

    static Map<Direction, ItemFluidPreview.FaceUv> textures(){
        var res = new EnumMap<Direction, ItemFluidPreview.FaceUv>(Direction.class);
        for(var dir : Direction.values()){
            res.put(dir, new ItemFluidPreview.FaceUv(Vec3.ZERO, new Vec3(1, 0, 0), new Vec3(0, 1, 0), .1F, .2F, .3F, 0, 0, .4F));
        }
        return res;
    }

    private static final class Sink implements VertexConsumer{
        private final List<Vec3> points = new ArrayList<>();
        private final List<Integer> colors = new ArrayList<>(), lights = new ArrayList<>();
        @Override public VertexConsumer addVertex(float x, float y, float z){ points.add(new Vec3(x, y, z)); return this; }
        @Override public VertexConsumer setColor(int red, int green, int blue, int alpha){ colors.add(alpha << 24 | red << 16 | green << 8 | blue); return this; }
        @Override public VertexConsumer setUv(float u, float v){ assertTrue(Float.isFinite(u + v)); return this; }
        @Override public VertexConsumer setUv1(int u, int v){ return this; }
        @Override public VertexConsumer setUv2(int u, int v){ lights.add(u | v << 16); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z){ return this; }
    }
}
