package com.rieno.gadgetsandgizmos.lib.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.*;

class DeferredWorkSchedulerTest{
    @Test void batchesIndependentQueriesWithoutExceedingTheTickAdmissionCount(){
        Thread owner = Thread.currentThread();
        AtomicInteger calls = new AtomicInteger();
        try(var scheduler = new DeferredWorkScheduler()){
            var res = scheduler.submit(() -> DeferredWorkScheduler.queryTogether(java.util.Collections.nCopies(160,
                    () -> scheduler.onOwnerThread(() -> {
                        assertSame(owner, Thread.currentThread());
                        return calls.incrementAndGet();
                    }))));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
            int ticks = 0;
            while(!res.isDone()){
                assertTrue(System.nanoTime() < deadline);
                int prev = calls.get();
                scheduler.tick();
                ticks++;
                assertTrue(calls.get() - prev <= 64);
                LockSupport.parkNanos(1_000_000L);
            }
            assertEquals(160, res.join().size());
            assertEquals(160, calls.get());
            assertTrue(ticks >= 3);
        }
    }

    @Test void runsWorldQueriesOnTheTickThreadAcrossSeveralTicks() throws Exception{
        Thread owner = Thread.currentThread();
        AtomicInteger calls = new AtomicInteger();
        try(var scheduler = new DeferredWorkScheduler()){
            CompletableFuture<Integer> res = scheduler.submit(() -> {
                assertNotSame(owner, Thread.currentThread());
                for(int idx = 0; idx < 8; idx++) scheduler.onOwnerThread(() -> {
                    assertSame(owner, Thread.currentThread());
                    calls.incrementAndGet();
                    LockSupport.parkNanos(3_000_000L);
                    return true;
                });
                return calls.get();
            });
            assertFalse(res.isDone());
            while(calls.get() == 0){ scheduler.tick(); LockSupport.parkNanos(1_000_000L); }
            assertEquals(1, calls.get());
            assertFalse(res.isDone());
            drain(scheduler, res);
            assertEquals(8, res.join());
        }
    }

    @Test void checkpointsResumeTheSameSearchAndShutdownCancelsBlockedQueries() throws Exception{
        try(var scheduler = new DeferredWorkScheduler()){
            AtomicInteger steps = new AtomicInteger();
            CountDownLatch started = new CountDownLatch(1);
            CompletableFuture<Integer> res = scheduler.submit(() -> {
                started.countDown();
                for(int idx = 0; idx < 1024; idx++){
                    steps.incrementAndGet();
                    DeferredWorkScheduler.checkpoint();
                }
                return steps.get();
            });
            assertTrue(started.await(2L, TimeUnit.SECONDS));
            drain(scheduler, res);
            assertEquals(1024, res.join());
            CompletableFuture<Boolean> blocked = scheduler.submit(() -> scheduler.onOwnerThread(() -> true));
            scheduler.close();
            assertTrue(blocked.isCancelled());
        }
    }

    @Test void joinsParallelChildrenWithoutExhaustingTheComputePool(){
        try(var scheduler = new DeferredWorkScheduler()){
            Thread owner = Thread.currentThread();
            AtomicInteger reads = new AtomicInteger();
            var res = scheduler.submit(() -> DeferredWorkScheduler.parallel(java.util.List.of(
                    () -> scheduler.onOwnerThread(() -> {
                        assertSame(owner, Thread.currentThread());
                        return reads.incrementAndGet();
                    }),
                    () -> scheduler.onOwnerThread(() -> {
                        assertSame(owner, Thread.currentThread());
                        return reads.incrementAndGet();
                    }))));
            drain(scheduler, res);
            assertEquals(2, res.join().size());
            assertEquals(2, reads.get());
        }
    }

    @Test void severalParentsCanJoinDeferredIndexSearchesTogether(){
        try(var scheduler = new DeferredWorkScheduler()){
            Thread owner = Thread.currentThread();
            AtomicInteger reads = new AtomicInteger();
            java.util.List<CompletableFuture<java.util.List<Integer>>> requests = new java.util.ArrayList<>();
            for(int idx = 0; idx < 8; idx++) requests.add(scheduler.submit(() ->
                    DeferredWorkScheduler.parallel(java.util.Collections.nCopies(4, () -> scheduler.onOwnerThread(() -> {
                        assertSame(owner, Thread.currentThread());
                        return reads.incrementAndGet();
                    })))));
            var res = CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new));
            drain(scheduler, res);
            assertEquals(32, reads.get());
            for(var request : requests) assertEquals(4, request.join().size());
        }
    }

    static void drain(DeferredWorkScheduler scheduler, CompletableFuture<?> res){
        long deadline = System.nanoTime() + 5_000_000_000L;
        while(!res.isDone()){
            assertTrue(System.nanoTime() < deadline, "Deferred work did not complete");
            scheduler.tick();
            LockSupport.parkNanos(1_000_000L);
        }
    }
}
