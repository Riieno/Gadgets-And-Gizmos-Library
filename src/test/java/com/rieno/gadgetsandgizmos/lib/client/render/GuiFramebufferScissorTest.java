package com.rieno.gadgetsandgizmos.lib.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuiFramebufferScissorTest {
    @Test
    void mapsCanvasIntoTheOffscreenTargetInsteadOfTheWindow() {
        assertEquals(new GuiFramebufferScissor.Bounds(240, 0, 528, 384),
                GuiFramebufferScissor.bounds(300, 60, 960, 540,
                        960, 540, 768, 432));
    }

    @Test
    void keepsClippedRegionsInsideTheFramebuffer() {
        assertEquals(new GuiFramebufferScissor.Bounds(0, 0, 768, 432),
                GuiFramebufferScissor.bounds(-100, -100, 2000, 1000,
                        960, 540, 768, 432));
    }
}
