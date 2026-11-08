package com.rieno.gadgetsandgizmos.lib.navigation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NavigationProgressTest{
    @Test
    void estimatesIncludeAccelerationCruiseAndFinalBraking(){
        assertEquals(15, NavigationProgress.estimateSeconds(100, 0, 0, 10, 2, 2));
        assertEquals(15, NavigationProgress.estimateSeconds(100, 0, 0, 100, 2, 2));
        assertTrue(NavigationProgress.estimateSeconds(100, 0, 0, 100, 20, 20)
                < NavigationProgress.estimateSeconds(100, 0, 0, 10, 2, 2));
    }

    @Test
    void estimatesUpdateWithRemainingDistanceAndActualMotion(){
        long starting = NavigationProgress.estimateSeconds(100, 0, 0, 10, 2, 2);
        long moving = NavigationProgress.estimateSeconds(100, 0, 10, 10, 2, 2);
        long closer = NavigationProgress.estimateSeconds(50, 0, 10, 10, 2, 2);
        assertEquals(13, moving);
        assertEquals(8, closer);
        assertTrue(starting > moving && moving > closer);
        assertEquals(0, NavigationProgress.estimateSeconds(1, 1, 0, 0, 2, 2));
        assertEquals(2, NavigationProgress.estimateSeconds(10, 0, 10, 10, 2, 2));
    }

    @Test
    void unknownAuthorityUsesMeasuredSpeedWithoutInventingATimeForStoppedCommands(){
        assertEquals(10, NavigationProgress.estimateSeconds(100, 0, 10, 20, 0, 0));
        assertEquals(5, NavigationProgress.estimateSeconds(100, 0, 0, 20, 0, 0));
        assertEquals(-1, NavigationProgress.estimateSeconds(100, 0, 0, 0, 2, 2));
        assertEquals(-1, NavigationProgress.estimateSeconds(Double.NaN, 0, 10, 20, 2, 2));
        assertEquals(-1, NavigationProgress.estimateSeconds(100, 0, Double.NaN, 20, 2, 2));
        assertFalse(NavigationProgress.INACTIVE.active());
    }
}
