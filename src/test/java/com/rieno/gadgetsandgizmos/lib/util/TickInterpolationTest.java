package com.rieno.gadgetsandgizmos.lib.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Exercise receiving, shortening and stopping an animated linkage
class TickInterpolationTest{
    // A recreated consumer must begin at the received span instead of zero
    @Test void firstUpdateStartsAtTheReceivedValue(){
        var motion = new TickInterpolation();
        motion.update(5.75, 20, false);
        assertEquals(5.75, motion.sample(20, 0));
        assertEquals(5.75, motion.sample(20, .5F));
    }

    // The last moving interval must never restart as partial ticks wrap
    @Test void motionSettlesWithoutAnotherPacket(){
        var motion = new TickInterpolation();
        motion.update(5.5, 20, false);
        motion.update(5.75, 21, false);
        assertEquals(5.625, motion.sample(21, .5F));
        for(long tick = 22; tick < 100; tick++){
            assertEquals(5.75, motion.sample(tick, 0));
            assertEquals(5.75, motion.sample(tick, .5F));
        }
        motion.update(5.75, 100, false);
        assertEquals(5.75, motion.sample(100, 0));
    }

    // A new section count must discard the previous chain's animation interval
    @Test void shorteningAndReloadingReplaceTheBaseline(){
        var motion = new TickInterpolation();
        motion.update(5.5, 20, false);
        motion.update(5.75, 21, false);
        motion.update(5, 21, true);
        assertEquals(5, motion.sample(21, 0));
        assertEquals(5, motion.sample(22, .9F));
        motion.update(4.75, 23, false);
        assertEquals(4.875, motion.sample(23, .5F));
        motion.update(3, 1, false);
        assertEquals(3, motion.sample(1, 0));
    }

    // Clamp unusual render fractions and reject nonfinite update data
    @Test void invalidInputsCannotPoisonTheAnimation(){
        var motion = new TickInterpolation();
        motion.reset(1, 1);
        motion.update(2, 2, false);
        assertEquals(1, motion.sample(2, -1));
        assertEquals(2, motion.sample(2, 2));
        assertEquals(1, motion.sample(2, Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> motion.update(Double.NaN, 3, false));
        assertEquals(2, motion.sample(3, 0));
    }
}
