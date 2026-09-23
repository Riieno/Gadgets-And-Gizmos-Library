package com.rieno.gadgetsandgizmos.lib.navigation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GroundSupportSafetyTest {
    @Test
    void stopsBeforeASustainedTerrainHole() {
        double distance = GroundSupportSafety.supportedDistance(
                12.0D, 0.5D, 1.0D, sample -> sample < 4.0D);

        assertEquals(3.5D, distance, 1.0E-8D);
    }

    @Test
    void permitsShortUnevenSupportGaps() {
        double distance = GroundSupportSafety.supportedDistance(
                12.0D, 0.5D, 1.0D,
                sample -> sample < 4.0D || sample > 4.5D);

        assertEquals(12.0D, distance, 1.0E-8D);
    }
}
