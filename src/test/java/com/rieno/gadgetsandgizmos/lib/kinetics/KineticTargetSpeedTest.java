package com.rieno.gadgetsandgizmos.lib.kinetics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KineticTargetSpeedTest {
    @Test
    void capsOutputAtInputSpeed() {
        assertEquals(1.0f, KineticTargetSpeed.outputRatio(32.0f, 64.0D));
        assertEquals(0.5f, KineticTargetSpeed.outputRatio(-32.0f, 16.0D));
        assertEquals(0.0f, KineticTargetSpeed.outputRatio(0.0f, 16.0D));
        assertEquals(1.0f, KineticTargetSpeed.outputRatio(0.0f, Double.NaN));
    }
}
