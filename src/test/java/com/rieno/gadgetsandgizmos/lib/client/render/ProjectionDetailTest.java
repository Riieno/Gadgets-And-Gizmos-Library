package com.rieno.gadgetsandgizmos.lib.client.render;

import com.rieno.gadgetsandgizmos.lib.hologram.HologramResolution;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectionDetailTest{
    @Test void fullAlwaysKeepsTexturesAndReducedLevelsHaveHysteresis(){
        for(double size : new double[]{0, .1, 1, 10, Double.NaN, Double.POSITIVE_INFINITY}) assertEquals(0, HologramResolution.FULL.level(size, 3));
        assertEquals(2, HologramResolution.LOW.level(9, -1));
        assertEquals(2, HologramResolution.LOW.level(6, -1));
        assertEquals(2, HologramResolution.LOW.level(3, -1));
        assertEquals(3, HologramResolution.LOW.level(1, -1));
        assertEquals(2, HologramResolution.LOW.level(7.8, 0));
        assertEquals(2, HologramResolution.LOW.level(8.2, 1));
        assertEquals(0, HologramResolution.LOW.level(Double.NaN, 3));
        assertEquals(2, HologramResolution.LOW.level(Double.POSITIVE_INFINITY, 0));
        assertEquals(1, HologramResolution.MEDIUM.level(200, 0));
        assertEquals(0, HologramResolution.HIGH.level(200, 2));
        assertEquals(0, HologramResolution.HIGH.level(1.9, 0));
        assertEquals(1, HologramResolution.HIGH.level(2.1, 1));
    }
    @Test void sizeFollowsProjectionScaleAndViewport(){
        var matrix = new Matrix4f().ortho(-100, 100, -100, 100, -100, 100);
        double pixels = ProjectionDetail.blockPixels(matrix, 0, 16, 1000, 1000);
        assertEquals(5, pixels, .001);
        assertEquals(pixels / 2, ProjectionDetail.blockPixels(new Matrix4f(matrix).scale(.5F), 0, 16, 1000, 1000), .001);
        assertEquals(pixels * 2, ProjectionDetail.blockPixels(matrix, 0, 16, 2000, 2000), .001);
    }
    @Test void nearPlaneIntersectionsUseTheSelectedQualityMinimum(){
        var matrix = new Matrix4f().perspective((float)Math.toRadians(70), 1, .1F, 1000);
        double size = ProjectionDetail.blockPixels(matrix, 0, 16, 1000, 1000);
        assertEquals(Double.POSITIVE_INFINITY, size);
        assertEquals(0, HologramResolution.FULL.level(size, 2));
        assertEquals(0, HologramResolution.HIGH.level(size, 2));
        assertEquals(1, HologramResolution.MEDIUM.level(size, 0));
        assertEquals(2, HologramResolution.LOW.level(size, 0));
    }
}
