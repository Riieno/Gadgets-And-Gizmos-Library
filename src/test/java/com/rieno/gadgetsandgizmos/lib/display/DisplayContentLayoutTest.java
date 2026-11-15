package com.rieno.gadgetsandgizmos.lib.display;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DisplayContentLayoutTest{
    @Test void invertsRotationInSurfacePixelsRatherThanAuthoredCanvasUnits(){
        for(int[] surface : new int[][]{{60, 60}, {180, 44}, {30, 120}}) for(int angle : new int[]{0, 27, 90, -45}){
            var layout = DisplayContentLayout.resolve(35, 20, 245, 130, .65, angle, 320, 180, surface[0], surface[1]);
            double x = layout.width() * -.35, y = layout.height() * .3;
            double rad = Math.toRadians(angle);
            double px = layout.x() + layout.width() * .5 + (x * Math.cos(rad) - y * Math.sin(rad)) * layout.scale();
            double py = layout.y() + layout.height() * .5 + (x * Math.sin(rad) + y * Math.cos(rad)) * layout.scale();
            assertArrayEquals(new double[]{.15, .8}, layout.point(px, py), 1e-7);
        }
    }

    @Test void excludesContentEdgesAndNonfinitePointers(){
        var layout = DisplayContentLayout.fill(60, 44);
        assertNull(layout.point(60, 20)); assertNull(layout.point(10, Double.NaN));
        assertArrayEquals(new double[]{.5, .5}, layout.point(30, 22));
    }
}
