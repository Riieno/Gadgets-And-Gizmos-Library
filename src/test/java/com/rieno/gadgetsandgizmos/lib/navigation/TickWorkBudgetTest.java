package com.rieno.gadgetsandgizmos.lib.navigation;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class TickWorkBudgetTest{
    @Test
    void chargesActualWorkAndResetsOnlyOnTheNextTick(){
        AtomicLong clock = new AtomicLong();
        TickWorkBudget budget = new TickWorkBudget(100L, 60L, clock::get);
        try(var work = budget.claim(1L)){
            assertTrue(work.available());
            clock.addAndGet(40L);
            assertEquals(20L, work.remainingNanos());
        }
        clock.addAndGet(1000L);
        try(var work = budget.claim(1L)){
            assertEquals(60L, work.remainingNanos());
            clock.addAndGet(60L);
            assertFalse(work.available());
        }
        try(var work = budget.claim(1L)){
            assertFalse(work.available());
        }
        try(var work = budget.claim(2L)){
            assertTrue(work.available());
        }
    }

    @Test
    void closingTwiceDoesNotChargeOtherControllers(){
        AtomicLong clock = new AtomicLong();
        TickWorkBudget budget = new TickWorkBudget(100L, 100L, clock::get);
        var work = budget.claim(0L);
        clock.addAndGet(30L);
        work.close();
        work.close();
        try(var next = budget.claim(0L)){
            assertEquals(70L, next.remainingNanos());
        }
    }
}
