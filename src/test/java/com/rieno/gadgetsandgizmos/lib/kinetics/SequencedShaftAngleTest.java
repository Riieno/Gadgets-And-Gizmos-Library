package com.rieno.gadgetsandgizmos.lib.kinetics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SequencedShaftAngleTest{
    @Test void tickPaddingCannotOvershootRepeatedTurns(){
        var angle = new SequencedShaftAngle();
        for(int turn = 1; turn <= 40; turn++){
            angle.start(90.0D);
            for(int tick = 0; tick < 12; tick++) angle.advance(128.0F);
            assertEquals(turn * 90.0D, angle.angleDegrees(), 1.0E-9D);
            assertEquals(0.0D, angle.remainingDegrees());
        }
    }

    @Test void fastAndReverseOutputsStillUseTheRequestedAngle(){
        var angle = new SequencedShaftAngle();
        for(float rpm : new float[]{256.0F, -256.0F, -128.0F, 128.0F}){
            double start = angle.angleDegrees();
            angle.start(35.0D);
            for(int tick = 0; tick < 10; tick++) angle.advance(rpm);
            assertEquals(start + Math.copySign(35.0D, rpm), angle.angleDegrees(), 1.0E-9D);
        }
        assertEquals(0.0D, angle.angleDegrees(), 1.0E-9D);
    }

    @Test void pausesAndSpeedChangesPreserveUnfinishedTravel(){
        var angle = new SequencedShaftAngle();
        angle.start(90.0D);
        angle.advance(32.0F);
        angle.advance(0.0F);
        angle.advance(64.0F);
        assertEquals(28.8D, angle.angleDegrees(), 1.0E-9D);
        for(int tick = 0; tick < 20; tick++) angle.advance(16.0F);
        assertEquals(90.0D, angle.angleDegrees(), 1.0E-9D);
    }

    @Test void savedMotionContinuesWithoutRepeatingTheTurn(){
        var angle = new SequencedShaftAngle();
        angle.start(90.0D);
        angle.advance(-128.0F);
        var restored = new SequencedShaftAngle();
        restored.restore(angle.angleDegrees(), angle.remainingDegrees());
        for(int tick = 0; tick < 10; tick++) restored.advance(-128.0F);
        assertEquals(-90.0D, restored.angleDegrees(), 1.0E-9D);
        restored.start(0.0D);
        restored.advance(256.0F);
        assertEquals(-90.0D, restored.angleDegrees(), 1.0E-9D);
    }

    @Test void distanceInstructionsCanRotateContinuously(){
        var angle = new SequencedShaftAngle();
        angle.start(Double.POSITIVE_INFINITY);
        for(int tick = 0; tick < 100; tick++) angle.advance(10.0F);
        assertEquals(300.0D, angle.angleDegrees(), 1.0E-9D);
        assertEquals(Double.POSITIVE_INFINITY, angle.remainingDegrees());
    }
}
