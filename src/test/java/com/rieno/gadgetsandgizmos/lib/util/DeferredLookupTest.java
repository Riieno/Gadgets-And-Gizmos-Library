package com.rieno.gadgetsandgizmos.lib.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class DeferredLookupTest{
    @Test void reusesPendingAndCompletedResultsThenExpiresThem(){
        try(var scheduler = new DeferredWorkScheduler()){
            DeferredLookup<String, Integer> cache = new DeferredLookup<>(2, 2L);
            AtomicInteger searches = new AtomicInteger();
            Supplier<Integer> task = () -> scheduler.onOwnerThread(searches::incrementAndGet);
            assertThrows(DeferredWorkScheduler.Pending.class, () -> cache.get(scheduler, "item", task));
            assertThrows(DeferredWorkScheduler.Pending.class, () -> cache.get(scheduler, "item", task));
            int first = poll(scheduler, () -> cache.get(scheduler, "item", task));
            assertEquals(1, first);
            assertEquals(first, cache.get(scheduler, "item", task));
            scheduler.tick();
            scheduler.tick();
            assertEquals(2, poll(scheduler, () -> cache.get(scheduler, "item", task)));
        }
    }

    @Test void evictsCompletedEntriesAndClearsPendingWork(){
        try(var scheduler = new DeferredWorkScheduler()){
            DeferredLookup<String, Integer> cache = new DeferredLookup<>(1, 100L);
            AtomicInteger searches = new AtomicInteger();
            Supplier<Integer> task = () -> scheduler.onOwnerThread(searches::incrementAndGet);
            assertEquals(1, poll(scheduler, () -> cache.get(scheduler, "first", task)));
            assertEquals(2, poll(scheduler, () -> cache.get(scheduler, "second", task)));
            assertEquals(3, poll(scheduler, () -> cache.get(scheduler, "first", task)));
            cache.clear();
            assertThrows(DeferredWorkScheduler.Pending.class, () -> cache.get(scheduler, "pending", task));
            cache.clear();
            assertEquals(4, poll(scheduler, () -> cache.get(scheduler, "pending", task)));
        }
    }

    @Test void finishesTheOriginalRequestWhenLiveStockChangesItsCacheKey(){
        try(var scheduler = new DeferredWorkScheduler()){
            DeferredLookup<String, Integer> cache = new DeferredLookup<>(2, 20L);
            AtomicInteger searches = new AtomicInteger();
            Supplier<Integer> original = () -> scheduler.onOwnerThread(() -> {
                searches.incrementAndGet();
                return 7;
            });
            Supplier<Integer> changed = () -> scheduler.onOwnerThread(() -> {
                searches.incrementAndGet();
                return 12;
            });
            assertThrows(DeferredWorkScheduler.Pending.class,
                    () -> cache.getForRequest(scheduler, "order", "old stock", original));
            assertEquals(7, poll(scheduler, () -> cache.getForRequest(scheduler, "order", "new stock", changed)));
            assertEquals(1, searches.get());
            assertEquals(12, poll(scheduler, () -> cache.getForRequest(scheduler, "order", "new stock", changed)));
            assertEquals(2, searches.get());
        }
    }

    @Test void keepsCompletedResultsUntilPendingRequestsConsumeThem(){
        try(var scheduler = new DeferredWorkScheduler()){
            DeferredLookup<String, Integer> cache = new DeferredLookup<>(2, 1L);
            AtomicInteger searches = new AtomicInteger();
            Supplier<Integer> task = () -> scheduler.onOwnerThread(searches::incrementAndGet);
            assertThrows(DeferredWorkScheduler.Pending.class,
                    () -> cache.getForRequest(scheduler, "order", "item", task));
            assertEquals(1, poll(scheduler, () -> cache.get(scheduler, "item", task)));
            scheduler.tick();
            scheduler.tick();
            assertEquals(1, cache.get(scheduler, "item", task));
            assertEquals(1, cache.getForRequest(scheduler, "order", "new stock", task));
            assertEquals(1, searches.get());
            assertEquals(2, poll(scheduler, () -> cache.get(scheduler, "item", task)));
        }
    }

    @Test void reclaimsAbandonedCompletedRequestsWhenCapacityIsReached(){
        try(var scheduler = new DeferredWorkScheduler()){
            DeferredLookup<String, Integer> cache = new DeferredLookup<>(1, 100L);
            AtomicInteger searches = new AtomicInteger();
            Supplier<Integer> task = () -> scheduler.onOwnerThread(searches::incrementAndGet);
            assertThrows(DeferredWorkScheduler.Pending.class,
                    () -> cache.getForRequest(scheduler, "abandoned order", "first", task));
            assertEquals(1, poll(scheduler, () -> cache.get(scheduler, "first", task)));
            assertEquals(2, poll(scheduler, () -> cache.getForRequest(scheduler, "new order", "second", task)));
        }
    }

    private static <T> T poll(DeferredWorkScheduler scheduler, Supplier<T> task){
        long deadline = System.nanoTime() + 5_000_000_000L;
        while(true){
            try{ return task.get(); }
            catch(DeferredWorkScheduler.Pending pending){
                assertTrue(System.nanoTime() < deadline, "Lookup did not finish");
                scheduler.tick();
                LockSupport.parkNanos(1_000_000L);
            }
        }
    }
}
