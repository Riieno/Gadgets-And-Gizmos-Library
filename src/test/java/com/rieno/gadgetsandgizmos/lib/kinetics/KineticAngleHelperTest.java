package com.rieno.gadgetsandgizmos.lib.kinetics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Check physical shaft motion independently of world time and model alignment
class KineticAngleHelperTest{
    @Test void stationaryShaftKeepsItsAngle(){
        assertEquals(0.0D, KineticAngleHelper.advanceRotationAngleDegrees(0.0D, 0.0F, 24000L));
        assertEquals(31.0D, KineticAngleHelper.advanceRotationAngleDegrees(31.0D, 0.0F, 100L));
    }

    @Test void speedChangesAndReversalsAccumulateOnlyActualMotion(){
        double angle = KineticAngleHelper.advanceRotationAngleDegrees(0.0D, 10.0F, 5L);
        angle = KineticAngleHelper.advanceRotationAngleDegrees(angle, 20.0F, 2L);
        assertEquals(27.0D, angle, 1.0E-9D);
        assertEquals(0.0D, KineticAngleHelper.advanceRotationAngleDegrees(angle, -10.0F, 9L), 1.0E-9D);
    }

    @Test void repeatedSamplesAndClockResetsDoNotMoveTheShaft(){
        assertEquals(12.0D, KineticAngleHelper.advanceRotationAngleDegrees(12.0D, 32.0F, 0L));
        assertEquals(12.0D, KineticAngleHelper.advanceRotationAngleDegrees(12.0D, 32.0F, -1L));
    }

    @Test void accumulatedMotionWrapsAcrossFullTurns(){
        assertEquals(-170.0D, KineticAngleHelper.advanceRotationAngleDegrees(160.0D, 10.0F, 10L));
        assertEquals(170.0D, KineticAngleHelper.advanceRotationAngleDegrees(-160.0D, -10.0F, 10L));
    }
}
