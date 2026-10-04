package com.rieno.gadgetsandgizmos.lib.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ScreenLineClipperTest {
    @Test
    void keepsTheVisiblePathFromOffscreenPoints() {
        assertEquals(new ScreenLineClipper.Segment(0, 5, 5, 5),
                ScreenLineClipper.clip(-10, 5, 5, 5, 0, 0, 10, 10));
        assertEquals(new ScreenLineClipper.Segment(5, 5, 9, 5),
                ScreenLineClipper.clip(5, 5, 20, 5, 0, 0, 10, 10));
        assertEquals(new ScreenLineClipper.Segment(0, 5, 9, 5),
                ScreenLineClipper.clip(-10, 5, 20, 5, 0, 0, 10, 10));
        assertNull(ScreenLineClipper.clip(-10, -10, -5, -5, 0, 0, 10, 10));
    }
}
