package com.rieno.gadgetsandgizmos.lib.scm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScmTankSteeringTest {
    @Test
    void requiresBothLongitudinalAndYawKineticGroups(){
        assertTrue(ScmTankSteering.isConfigured(2, 1));
        assertFalse(ScmTankSteering.isConfigured(2, 0));
        assertFalse(ScmTankSteering.isConfigured(0, 2));
    }

    @Test
    void scoresEachTrackDirectionAgainstForwardAndYaw(){
        ScmTankSteering.Demand demand = ScmTankSteering.demand(0.5D, 1.0D);

        assertEquals(1.0D, demand.controlLevel(), 1.0E-8D);
        assertTrue(demand.authorityScore(1.0D, 1.0D)
                > demand.authorityScore(1.0D, -1.0D));
    }

    @Test
    void removesAlternatingStraightLineYawNoise(){
        assertEquals(0.0D, ScmTankSteering.demand(1.0D, 0.04D).yawRight(), 1.0E-8D);
        assertEquals(0.0D, ScmTankSteering.demand(-1.0D, -0.08D).yawRight(), 1.0E-8D);
        assertTrue(ScmTankSteering.isTurning(0.25D));
    }
}
