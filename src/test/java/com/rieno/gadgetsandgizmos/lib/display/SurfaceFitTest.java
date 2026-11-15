package com.rieno.gadgetsandgizmos.lib.display;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SurfaceFitTest{
    @Test void fittedPixelsMapToTheirOwnCellsAcrossSurfaceShapes(){
        for(int[] size : new int[][]{{60, 60}, {180, 44}, {30, 120}, {1280, 720}}){
            var fit = SurfaceFit.fit(306, 171, size[0], size[1], true);
            assertEquals(fit.scaleX(), fit.scaleY());
            for(int col : new int[]{0, 5, 25, 50}) for(int row : new int[]{0, 2, 9, 18}){
                var point = fit.point(fit.x() + (col + .5) * 6 * fit.scaleX(), fit.y() + (row + .5) * 9 * fit.scaleY());
                assertNotNull(point); assertEquals(col, (int)Math.floor(point[0] * 51)); assertEquals(row, (int)Math.floor(point[1] * 19));
            }
            if(fit.x() > 0) assertNull(fit.point(fit.x() / 2, size[1] / 2.0));
            if(fit.y() > 0) assertNull(fit.point(size[0] / 2.0, fit.y() / 2));
        }
    }

    @Test void stretchedSourcesContinueUsingTheWholeViewport(){
        var fit = SurfaceFit.fit(306, 171, 60, 60, false);
        assertEquals(0, fit.x()); assertEquals(0, fit.y());
        assertArrayEquals(new double[]{.1, .9}, fit.point(6, 54), 1e-10);
    }

    @Test void rejectsInvalidDimensions(){
        assertThrows(IllegalArgumentException.class, () -> SurfaceFit.fit(0, 1, 60, 60, true));
        assertThrows(IllegalArgumentException.class, () -> SurfaceFit.fit(306, 171, Double.NaN, 60, true));
    }
}
