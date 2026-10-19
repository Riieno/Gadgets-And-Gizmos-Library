package com.rieno.gadgetsandgizmos.lib.display;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WidgetCanvasProjectionTest{
    @Test void surfaceCanvasFillsTheDisplayAndHologramSizeIsIndependent(){
        var surface = WidgetCanvasProjection.layout(false, 140, 72, 576, 180, 2, 1, 9, 8, 7, 45, 96);
        assertEquals(576, surface.width()); assertEquals(180, surface.height());
        assertEquals(0, surface.x()); assertEquals(0, surface.z()); assertEquals(0, surface.rotation());
        var hologram = WidgetCanvasProjection.layout(true, 140, 72, 576, 180, 2, 1, -1, 2, 0.5, 90, 96);
        assertEquals(192, hologram.width()); assertEquals(96, hologram.height());
        assertEquals(-96, hologram.x()); assertEquals(-192, hologram.y()); assertEquals(-48, hologram.z());
        var point = hologram.unproject(0, -144);
        assertEquals(70, point.x(), 1e-8); assertEquals(36, point.y(), 1e-8);
        var rotatedPoint = hologram.unproject(0, -120);
        assertEquals(87.5, rotatedPoint.x(), 1e-8); assertEquals(36, rotatedPoint.y(), 1e-8);
    }

    @Test void worldRayRespectsDepthReachParallelRaysAndRotatedPlanes(){
        var layout = WidgetCanvasProjection.layout(true, 100, 50, 300, 200, 2, 1, 0, 0, 0.5, 30, 96);
        Vec3 right = new Vec3(1, 0, 0), down = new Vec3(0, -1, 0);
        var hit = WidgetCanvasProjection.intersect(new Vec3(1, -0.5, 3), new Vec3(0, 0, -1), 4,
                Vec3.ZERO, right, down, layout, 96);
        assertNotNull(hit); assertEquals(2.5, hit.distance(), 1e-8);
        assertEquals(50, hit.x(), 1e-8); assertEquals(25, hit.y(), 1e-8);
        var offsetHit = WidgetCanvasProjection.intersect(new Vec3(1 + Math.cos(Math.PI / 6) / 2, -0.75, 3),
                new Vec3(0, 0, -1), 4, Vec3.ZERO, right, down, layout, 96);
        assertNotNull(offsetHit);
        assertEquals(75, offsetHit.x(), 1e-8); assertEquals(25, offsetHit.y(), 1e-8);
        assertNull(WidgetCanvasProjection.intersect(new Vec3(1, -0.5, 3), new Vec3(0, 0, -1), 2,
                Vec3.ZERO, right, down, layout, 96));
        assertNull(WidgetCanvasProjection.intersect(Vec3.ZERO, right, 10, Vec3.ZERO, right, down, layout, 96));
        assertNull(WidgetCanvasProjection.intersect(new Vec3(0, 0, -1), new Vec3(0, 0, -1), 10,
                Vec3.ZERO, right, down, layout, 96));
    }
}
