package com.rieno.gadgetsandgizmos.lib.geometry;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExteriorHullTest{
    private static boolean shell(int x, int y, int z){ return x == 0 || y == 0 || z == 0 || x == 4 || y == 4 || z == 4; }

    @Test void opaqueHullHidesEnclosedFacesButKeepsItsOutside(){
        var hull = new ExteriorHull(0, 0, 0, 4, 4, 4, ExteriorHullTest::shell);
        assertEquals(0, hull.visibleFaces(2, 2, 2));
        assertEquals(1 << 4, hull.visibleFaces(0, 2, 2));
        assertEquals(1 << 1, hull.visibleFaces(2, 4, 2));
    }

    @Test void glassAndOpenDoorsExposeTheConnectedInterior(){
        var hull = new ExteriorHull(0, 0, 0, 4, 4, 4, (x, y, z) -> shell(x, y, z) && !(x == 0 && y == 2 && z == 2));
        assertEquals(63, hull.visibleFaces(2, 2, 2));
        assertNotEquals(0, hull.visibleFaces(0, 2, 2));
    }

    @Test void transparentWindowDoesNotExposeASeparateSealedRoom(){
        var hull = new ExteriorHull(0, 0, 0, 8, 4, 4,
                (x, y, z) -> (x == 0 || x == 4 || x == 8 || y == 0 || y == 4 || z == 0 || z == 4) && !(x == 0 && y == 2 && z == 2));
        assertEquals(63, hull.visibleFaces(2, 2, 2));
        assertEquals(0, hull.visibleFaces(6, 2, 2));
    }

    @Test void chunkBoundariesAndNegativeCoordinatesDoNotOpenHoles(){
        var hull = new ExteriorHull(-18, -3, 14, -14, 1, 18, (x, y, z) -> shell(x + 18, y + 3, z - 14));
        assertEquals(0, hull.visibleFaces(-16, -1, 16));
        assertEquals(1 << 4, hull.visibleFaces(-18, -1, 16));
    }

    @Test void cutawaysExposeTheirInteriorAndHugeSparseBoundsRemainConservative(){
        var cut = new ExteriorHull(0, 0, 0, 4, 4, 4, (x, y, z) -> y < 4 && shell(x, y, z));
        assertEquals(63, cut.visibleFaces(2, 2, 2));
        var huge = new ExteriorHull(-30_000_000, -100, -30_000_000, 30_000_000, 100, 30_000_000, (x, y, z) -> true);
        assertEquals(63, huge.visibleFaces(0, 0, 0));
    }
}
