package com.rieno.gadgetsandgizmos.lib.color;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TextureColorAverageTest{
    @Test void transparentPixelsDoNotDiluteVisibleColor(){
        assertEquals(0x7FFF0000, TextureColorAverage.argb(2, idx -> idx == 0 ? 0xFF0000FF : 0x00FF0000));
        assertEquals(0, TextureColorAverage.argb(4, idx -> 0x000000FF));
        assertEquals(0, TextureColorAverage.argb(0, idx -> -1));
    }
    @Test void opaqueBaseAndCutoutOverlayRemainOpaque(){
        int overlay = TextureColorAverage.argb(2, idx -> idx == 0 ? 0xFF00FF00 : 0);
        int combined = TextureColorAverage.over(overlay, 0xFF804020);
        assertEquals(255, combined >>> 24);
        assertTrue((combined >> 8 & 255) > 64);
        assertEquals(0xFF112233, TextureColorAverage.over(0, 0xFF112233));
        assertEquals(0xFFABCDEF, TextureColorAverage.over(0xFFABCDEF, 0xFF112233));
        assertEquals(0, TextureColorAverage.over(0, 0));
    }
    @Test void translucentFacesStayTranslucentWithoutAnOpaqueBase(){
        assertEquals(128, TextureColorAverage.over(0x80FFFFFF, 0) >>> 24);
        assertTrue((TextureColorAverage.over(0x40FFFFFF, 0x40FFFFFF) >>> 24) < 255);
    }
}
