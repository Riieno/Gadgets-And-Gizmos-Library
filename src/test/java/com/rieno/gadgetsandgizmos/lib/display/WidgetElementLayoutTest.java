package com.rieno.gadgetsandgizmos.lib.display;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.lib.client.ui.HudWidgetRenderer;
import net.minecraft.nbt.CompoundTag;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Keep authored bounds and picking independent of nonuniform surface scaling
class WidgetElementLayoutTest{
    @Test void rotatedElementAndInversePickingAgreeAcrossSurfaceAspectRatios(){
        var data = element(37, 29, 91, 35, .7, 33);
        var element = WidgetElementLayout.resolve(data, 320, 180, true);
        for(int[] surface : new int[][]{{32, 16}, {96, 96}, {640, 180}, {140, 260}}){
            var canvas = WidgetCanvasProjection.layout(false, 320, 180, surface[0], surface[1], 1, 1, 0, 0, 0, 0, 64);
            var poses = new PoseStack();
            poses.scale((float)canvas.scaleX(), (float)canvas.scaleY(), 1);
            HudWidgetRenderer.transform(poses, element);
            var visible = poses.last().pose().transformPosition(91 * .25F, 35 * .75F, 0, new Vector3f());
            var point = canvas.unproject(visible.x, visible.y);
            var local = element.unproject(point.x(), point.y());
            assertEquals(.25, local.horizontalFraction(element.bounds()), 1e-5);
            assertEquals(.75, local.verticalFraction(element.bounds()), 1e-5);
            assertTrue(local.isInside(element.bounds()));
        }
    }

    @Test void fittingUsesTheAuthoredCanvasAndKeepsRotatedCornersInside(){
        var element = WidgetElementLayout.resolve(element(-17, 50, 140, 45, 4, 45), 120, 60, true);
        assertEquals(new DisplayWidgetProjection.Bounds(0, 15, 120, 45), element.bounds());
        for(int x : new int[]{0, 120}) for(int y : new int[]{0, 45}){
            var corner = element.project(x, y);
            assertTrue(corner.x() >= -1e-6 && corner.x() <= 120 + 1e-6);
            assertTrue(corner.y() >= -1e-6 && corner.y() <= 60 + 1e-6);
        }
    }

    @Test void rotatedCanvasCornersAndPickingUseTheSameNonuniformProjection(){
        var canvas = WidgetCanvasProjection.layout(true, 320, 180, 64, 32, 2.3, .8, 1.2, -.4, 2, 38, 64);
        for(double x : new double[]{-30, 0, 167.5, 320, 450}) for(double y : new double[]{-20, 0, 90, 180, 235}){
            var projected = canvas.project(x, y);
            var authored = canvas.unproject(projected.x(), projected.y());
            assertEquals(x, authored.x(), 1e-8);
            assertEquals(y, authored.y(), 1e-8);
        }
    }

    @Test void hologramsRetainAuthoredOverflowAndInputsRemainUnchanged(){
        CompoundTag data = element(-12, -8, 40, 20, 2, 15);
        CompoundTag before = data.copy();
        var element = WidgetElementLayout.resolve(data, 30, 10, false);
        assertEquals(new DisplayWidgetProjection.Bounds(-12, -8, 40, 20), element.bounds());
        assertEquals(2, element.scale());
        assertEquals(before, data);
        data.putDouble("Scale", Double.NaN);
        data.putDouble("Rotation", Double.POSITIVE_INFINITY);
        var invalid = WidgetElementLayout.resolve(data, 30, 10, false);
        assertEquals(1, invalid.scale());
        assertEquals(0, invalid.rotation());
    }

    @Test void resizingMissingCanvasDimensionsUsesTheSameDefaultsAsRendering(){
        var data = new CompoundTag();
        var elements = new net.minecraft.nbt.ListTag();
        elements.add(element(32, 18, 80, 45, 1, 0));
        data.put("WidgetElements", elements);
        assertEquals(320, WidgetCanvasProjection.canvasWidth(data));
        assertEquals(180, WidgetCanvasProjection.canvasHeight(data));
        WidgetCanvasProjection.resizeCanvas(data, 640, 90);
        var elm = elements.getCompound(0);
        assertEquals(64, elm.getInt("X"));
        assertEquals(9, elm.getInt("Y"));
        assertEquals(160, elm.getInt("W"));
        assertEquals(23, elm.getInt("H"));
    }

    private static CompoundTag element(int x, int y, int width, int height, double scale, double rotation){
        var data = new CompoundTag();
        data.putInt("X", x); data.putInt("Y", y); data.putInt("W", width); data.putInt("H", height);
        data.putDouble("Scale", scale); data.putDouble("Rotation", rotation);
        return data;
    }
}
