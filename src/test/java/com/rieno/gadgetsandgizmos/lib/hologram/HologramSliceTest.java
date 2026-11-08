package com.rieno.gadgetsandgizmos.lib.hologram;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HologramSliceTest{
    @Test
    void surfaceRetainsTenBlocksBelowTheTerrainAndEveryHigherPeak(){
        var slice = HologramSlice.surface(65, -64, 320);
        assertTrue(slice.surface());
        assertEquals(54, slice.minY());
        assertEquals(320, slice.maxY());
        assertFalse(slice.contains(53));
        assertTrue(slice.contains(54));
        assertTrue(slice.contains(64));
        assertTrue(slice.contains(319));
        assertFalse(slice.contains(320));
    }

    @Test
    void surfaceAndCutawayOffsetsHaveSeparateBounds(){
        var surface = HologramSlice.at(65, 0);
        assertTrue(surface.surface());
        assertEquals(54, surface.minY());
        assertEquals(surface.minY(), HologramSlice.projectionOriginY(65, 0));
        var underground = HologramSlice.at(65, -40);
        assertFalse(underground.surface());
        assertEquals(25, underground.maxY());
        assertEquals(25 - HologramSlice.CUTAWAY_DEPTH, underground.minY());
        assertEquals(25, HologramSlice.projectionOriginY(65, -40));
    }

    @Test
    void sparseAndExtremeHeightmapsStayInsideTheWorld(){
        assertEquals(-64, HologramSlice.surface(-1000, -64, 320).minY());
        assertEquals(319, HologramSlice.surface(1000, -64, 320).minY());
        assertEquals(-64, HologramSlice.surface(Double.NaN, -64, 320).minY());
        assertEquals(54, HologramSlice.surface(65.75, -64, 320).minY());
        assertThrows(IllegalArgumentException.class, () -> HologramSlice.surface(65, 320, -64));
    }
}
