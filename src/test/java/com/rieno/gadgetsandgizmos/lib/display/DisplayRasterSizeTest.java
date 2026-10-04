package com.rieno.gadgetsandgizmos.lib.display;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DisplayRasterSizeTest {
    @Test
    void capsLargeFramebuffersWithoutStretchingTheDisplay() {
        assertEquals(new DisplayRasterSize(768, 432),
                DisplayRasterSize.fit(3840, 2160, 768, 512));
        assertEquals(new DisplayRasterSize(360, 512),
                DisplayRasterSize.fit(1080, 1536, 768, 512));
        assertEquals(new DisplayRasterSize(320, 180),
                DisplayRasterSize.fit(320, 180, 768, 512));
    }
}
