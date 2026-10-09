package com.rieno.gadgetsandgizmos.lib.view;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

// Check aggregate capture limits under different player frame rates and render costs
class ViewCaptureBudgetTest{
    // High player FPS must not increase feed captures above ten per second
    @Test
    void frameRateDoesNotMultiplyCaptureWork(){
        for(int fps : new int[]{30, 100, 200, 1000}){
            var budget = new ViewCaptureBudget(10, 0.10D);
            int captures = 0;
            for(long now = 0; now < 1_000_000_000L; now += 1_000_000_000L / fps){
                if(!budget.ready(now)) continue;
                captures++;
                budget.completed(now + 1_000_000L, 1_000_000L);
            }
            assertTrue(captures <= 10, "fps=" + fps + ", captures=" + captures);
            assertTrue(captures >= 8);
        }
    }
    // Expensive CPU or GPU passes must consume a bounded share of wall time
    @Test
    void slowPassesBackOffAndResetWithTheWorld(){
        var budget = new ViewCaptureBudget(10, 0.10D);
        budget.completed(50_000_000L, 50_000_000L);
        assertFalse(budget.ready(499_999_999L));
        assertTrue(budget.ready(500_000_000L));
        budget.completed(600_000_000L, 100_000_000L);
        assertFalse(budget.ready(1_499_999_999L));
        assertTrue(budget.ready(1_500_000_000L));
        budget.reset();
        assertTrue(budget.ready(0));
    }
}
