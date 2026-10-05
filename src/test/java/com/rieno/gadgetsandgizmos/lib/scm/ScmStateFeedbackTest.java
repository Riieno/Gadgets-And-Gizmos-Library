package com.rieno.gadgetsandgizmos.lib.scm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScmStateFeedbackTest{
    @Test
    void lqrAndAckermannPlaceTheRequestedStablePoles(){
        ScmStateFeedback.Gains lqr = ScmStateFeedback.lqrForResponse(2.0D);
        ScmStateFeedback.Gains placed = ScmStateFeedback.ackermann(-0.5D, -0.5D);
        assertEquals(placed.position(), lqr.position(), 1.0E-10D);
        assertEquals(placed.rate(), lqr.rate(), 1.0E-10D);
        assertEquals(0.25D, ScmStateFeedback.acceleration(1.0D, 0.0D, lqr), 1.0E-10D);
        assertEquals(-1.0D, ScmStateFeedback.acceleration(0.0D, 1.0D, lqr), 1.0E-10D);
    }

    @Test
    void invalidControllerDesignIsRejected(){
        assertThrows(IllegalArgumentException.class,
                () -> ScmStateFeedback.lqr(1.0D, 0.0D, 0.0D));
        assertThrows(IllegalArgumentException.class,
                () -> ScmStateFeedback.ackermann(0.5D, -1.0D));
    }

    @Test
    void physicalFeedbackOpposesADescendingDisturbance(){
        ScmStateFeedback.Gains gains = ScmStateFeedback.lqrForResponse(0.75D);
        double upward = ScmStateFeedback.acceleration(0.5D, -2.0D, gains);
        assertEquals(6.222222222222222D, upward, 1.0E-9D);
        assertEquals(4.444444444444445D,
                ScmStateFeedback.velocityAcceleration(0.0D, -2.0D, 0.45D),
                1.0E-9D);
    }
}
