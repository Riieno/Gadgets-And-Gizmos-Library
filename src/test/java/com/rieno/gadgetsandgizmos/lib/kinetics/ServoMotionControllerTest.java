package com.rieno.gadgetsandgizmos.lib.kinetics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServoMotionControllerTest{
    @Test void smallContinuouslyChangingTargetsProduceSignedRpmUntilTheyStop(){
        for(int direction : new int[]{-1, 1}){
            var servo = servo();
            for(int tick = 1; tick <= 50; tick++){
                float previous = servo.getCurrentAngle();
                float target = direction * tick * 0.3F;
                servo.update(target);
                assertEquals(target, servo.getCurrentAngle(), 0.0001);
                assertEquals((target - previous) / 0.3F, servo.getGeneratedSpeed(), 0.0001);
                assertTrue(servo.getGeneratedSpeed() * direction > 0);
                assertTrue(servo.isMoving());
            }
            servo.update(servo.getCurrentAngle());
            assertEquals(0, servo.getGeneratedSpeed());
            assertFalse(servo.isMoving());
        }
    }

    @Test void arrivalStepReportsItsActualRpmRatherThanLosingTheLastMovement(){
        for(float target : new float[]{-90, 90}){
            var servo = servo();
            int ticks = 0;
            while(servo.getCurrentAngle() != target){
                float previous = servo.getCurrentAngle();
                servo.update(target);
                assertEquals((servo.getCurrentAngle() - previous) / 0.3F, servo.getGeneratedSpeed(), 0.0001);
                assertTrue(servo.isMoving());
                assertTrue(++ticks < 1000);
            }
            servo.update(target);
            assertEquals(0, servo.getGeneratedSpeed());
        }
    }

    @Test void explicitAngleLimitsOverrideTheDefaultAndSupportMultipleTurns(){
        assertEquals(90, ServoMotionController.resolveAngleLimit(Double.NaN, 90));
        assertEquals(360, ServoMotionController.resolveAngleLimit(360, 90));
        assertEquals(720, ServoMotionController.resolveAngleLimit(-720, 90));
        assertEquals(30, ServoMotionController.resolveAngleLimit(30, 90));
        assertEquals(0, ServoMotionController.resolveAngleLimit(0, 90));
        assertEquals(90, ServoMotionController.resolveAngleLimit(Double.POSITIVE_INFINITY, 90));
        assertEquals(0, ServoMotionController.resolveAngleLimit(Double.NaN, Double.NaN));
    }

    @Test void equalPositiveAndNegativeMovesHaveEqualSpeedAndDuration(){
        for(float angle : new float[]{15, 90, 180, 360, 720}){
            var forward = servo();
            var reverse = servo();
            int ticks = 0;
            do{
                forward.update(angle);
                reverse.update(-angle);
                assertEquals(-forward.getCurrentAngle(), reverse.getCurrentAngle(), 0.0001);
                assertEquals(-forward.getGeneratedSpeed(), reverse.getGeneratedSpeed(), 0.0001);
                assertEquals(forward.isMoving(), reverse.isMoving());
                assertTrue(++ticks < 1000);
            }while(forward.isMoving());
            assertEquals(angle, forward.getCurrentAngle(), 0.0001);
            assertEquals(-angle, reverse.getCurrentAngle(), 0.0001);
            assertEquals(0, forward.getGeneratedSpeed());
            assertEquals(0, reverse.getGeneratedSpeed());
        }
    }

    @Test void ReversingBetweenBothLimitsPreservesSignedRpm(){
        var left = servo();
        var right = servo();
        left.snapTo(90);
        right.snapTo(-90);
        for(int tick = 0; tick < 100; tick++){
            left.update(-90);
            right.update(90);
            assertEquals(-left.getCurrentAngle(), right.getCurrentAngle(), 0.0001);
            assertEquals(-left.getGeneratedSpeed(), right.getGeneratedSpeed(), 0.0001);
            if(left.isMoving()){
                assertTrue(left.getGeneratedSpeed() < 0);
                assertTrue(right.getGeneratedSpeed() > 0);
            }
        }
        assertEquals(-90, left.getCurrentAngle());
        assertEquals(90, right.getCurrentAngle());
    }

    private static ServoMotionController servo(){
        return new ServoMotionController(new ServoMotionController.ServoMotionConfig(32, 0.35F, 0.3F, 0.5F));
    }
}
