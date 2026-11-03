package com.rieno.gadgetsandgizmos.lib.control;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Verify that idle expiry clears the accumulator consumed by the next movement
final class MouseAxisStateTest{
    // Resume from zero at the exact timeout boundary
    @Test void expiredMovementStartsAtZero(){
        var axis = new MouseAxisState();
        axis.sample(0.7, 10);
        assertFalse(axis.expire(29, 20));
        assertTrue(axis.expire(30, 20));
        axis.sample(axis.value() + 0.1, 31);
        assertEquals(0.1, axis.value(), 1.0E-9);
        assertFalse(axis.expire(50, 20));
        assertTrue(axis.expire(51, 20));
        assertFalse(axis.expire(52, 20));
    }

    // Keep finite samples bounded and clear a stale clock after reconnecting
    @Test void clampedMotionRefreshesTimeoutAndRejectsInvalidSamples(){
        var axis = new MouseAxisState();
        axis.sample(2, 100);
        axis.sample(Double.NaN, 120);
        assertEquals(1, axis.value());
        assertTrue(axis.expire(120, 20));
        axis.sample(-2, 200);
        assertTrue(axis.expire(5, 20));
        assertEquals(0, axis.value());
    }
}
