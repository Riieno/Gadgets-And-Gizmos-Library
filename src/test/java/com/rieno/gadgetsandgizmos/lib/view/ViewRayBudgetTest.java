package com.rieno.gadgetsandgizmos.lib.view;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

// Verify rates below, above and between the game tick frequency
class ViewRayBudgetTest{
    @Test void oneRayPerSecondProducesTenRaysInTenSeconds(){ assertEquals(10, samples(1, 200)); }
    @Test void fractionalRatesRetainTheirLongTermBudget(){ assertEquals(75, samples(7.5, 200)); }
    @Test void sixtyRaysPerSecondProducesThreePerTick(){
        ViewRayBudget budget = new ViewRayBudget();
        for(int tick = 0; tick < 100; tick++) assertEquals(3, budget.next(tick, 60));
    }
    @Test void repeatedReadsAndUnloadedTimeDoNotAddRays(){
        ViewRayBudget budget = new ViewRayBudget();
        assertEquals(1, budget.next(0, 20));
        assertEquals(0, budget.next(0, 20));
        assertEquals(1, budget.next(10_000, 20));
    }
    @Test void invalidAndExcessiveRatesStayBounded(){
        ViewRayBudget budget = new ViewRayBudget();
        assertEquals(0, budget.next(0, Double.NaN));
        assertEquals(0, budget.next(1, -20));
        assertEquals(ViewRaycast.MAX_RAYS, budget.next(2, Double.MAX_VALUE));
    }
    private static int samples(double rate, int ticks){
        ViewRayBudget budget = new ViewRayBudget();
        int count = 0;
        for(int tick = 0; tick < ticks; tick++) count += budget.next(tick, rate);
        return count;
    }
}
