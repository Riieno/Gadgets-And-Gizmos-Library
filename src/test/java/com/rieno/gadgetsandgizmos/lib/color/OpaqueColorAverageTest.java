package com.rieno.gadgetsandgizmos.lib.color;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OpaqueColorAverageTest{
    @Test void readsAbgrChannelsAndAveragesOpaqueColors(){
        assertEquals(0xFF0000, OpaqueColorAverage.rgb(1, idx -> 0xFF0000FF));
        assertEquals(0x0000FF, OpaqueColorAverage.rgb(1, idx -> 0xFFFF0000));
        assertEquals(0x7F007F, OpaqueColorAverage.rgb(2, idx -> idx == 0 ? 0xFF0000FF : 0xFFFF0000));
    }
    @Test void transparencyAlwaysRetainsTheOriginalTexture(){
        assertEquals(-1, OpaqueColorAverage.rgb(3, idx -> idx == 1 ? 0x80FFFFFF : -1));
        assertEquals(-1, OpaqueColorAverage.rgb(0, idx -> -1));
    }
}
