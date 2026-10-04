package com.rieno.gadgetsandgizmos.lib.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RcsNozzlePowerTest {
    @Test
    void selfPoweredNozzleIgnoresShaftAndEmptyPressureTank() {
        assertEquals(215.0D, RcsNozzlePower.available(true, true, false, 0.0D, 215.0D, 256.0D));
        assertEquals(0.0D, RcsNozzlePower.available(false, true, false, 256.0D, 215.0D, 256.0D));
        assertEquals(215.0D, RcsNozzlePower.available(false, true, true, 0.0D, 215.0D, 256.0D));
        assertEquals(107.5D, RcsNozzlePower.available(false, false, false, -128.0D, 215.0D, 256.0D));
    }
}
