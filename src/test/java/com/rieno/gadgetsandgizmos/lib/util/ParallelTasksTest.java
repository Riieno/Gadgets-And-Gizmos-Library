package com.rieno.gadgetsandgizmos.lib.util;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

class ParallelTasksTest{
    @Test
    void concurrentCompletionRetainsInputOrderAndUsesWorkers() throws Exception{
        CountDownLatch worker = new CountDownLatch(1);
        Thread caller = Thread.currentThread();
        try(var executor = Executors.newFixedThreadPool(2)){
            var res = ParallelTasks.evaluate(List.of(() -> {
                try{ assertTrue(worker.await(5, TimeUnit.SECONDS)); }
                catch(InterruptedException err){ throw new AssertionError(err); }
                return 1;
            }, () -> {
                assertNotSame(caller, Thread.currentThread());
                worker.countDown();
                return 2;
            }), executor);
            assertEquals(List.of(1, 2), res);
            assertThrows(UnsupportedOperationException.class, () -> res.add(3));
        }
    }

    @Test
    void rejectionAndSequentialModeRunEachJobExactlyOnce(){
        for(boolean reject : new boolean[]{false, true}){
            AtomicInteger calls = new AtomicInteger();
            List<Supplier<Integer>> jobs = new ArrayList<>();
            for(int idx = 0; idx < 100; idx++){
                int val = idx;
                jobs.add(() -> { calls.incrementAndGet(); return val; });
            }
            var res = ParallelTasks.evaluate(jobs, reject ? task -> { throw new RejectedExecutionException(); } : null);
            assertEquals(100, calls.get());
            for(int idx = 0; idx < 100; idx++) assertEquals(idx, res.get(idx));
        }
    }

    @Test
    void failureWaitsForSiblingsAndPreservesTheFirstInputFailure(){
        AtomicInteger completed = new AtomicInteger();
        var expected = new IllegalArgumentException("first");
        try(var executor = Executors.newFixedThreadPool(2)){
            assertSame(expected, assertThrows(IllegalArgumentException.class, () -> ParallelTasks.evaluate(List.of(
                    () -> { throw expected; },
                    () -> { completed.incrementAndGet(); throw new IllegalStateException("second"); },
                    () -> { completed.incrementAndGet(); return 3; }), executor)));
            assertEquals(2, completed.get());
        }
    }

    @Test
    void interruptionIsRestoredAfterEveryJobFinishes(){
        try(var executor = Executors.newFixedThreadPool(1)){
            Thread.currentThread().interrupt();
            try{
                assertEquals(List.of(1, 2), ParallelTasks.evaluate(List.of(() -> 1, () -> 2), executor));
                assertTrue(Thread.currentThread().isInterrupted());
            }finally{ Thread.interrupted(); }
        }
    }

    @Test
    void nestedWorkerBatchesDoNotWaitForTheirOwnPool(){
        try(var executor = Executors.newFixedThreadPool(1)){
            assertEquals(List.of(0, 3), ParallelTasks.evaluate(List.of(() -> 0,
                    () -> ParallelTasks.<Integer>evaluate(List.of(() -> 1, () -> 2), executor).stream()
                            .mapToInt(Integer::intValue).sum()), executor));
        }
    }

    @Test
    void sharedPoolKeepsItsWorkerBoundUnderCompetingCallers() throws Exception{
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        try(var callers = Executors.newFixedThreadPool(8)){
            var batches = new ArrayList<java.util.concurrent.Future<?>>();
            for(int batch = 0; batch < 32; batch++){
                batches.add(callers.submit(() -> {
                    List<Supplier<Integer>> jobs = new ArrayList<>();
                    for(int idx = 0; idx < 16; idx++){
                        int val = idx;
                        jobs.add(() -> {
                            boolean worker = Thread.currentThread().getName().startsWith("gadgetsngizmos-compute-");
                            if(worker) peak.accumulateAndGet(active.incrementAndGet(), Math::max);
                            try{ Thread.yield(); completed.incrementAndGet(); return val; }
                            finally{ if(worker) active.decrementAndGet(); }
                        });
                    }
                    var res = ParallelTasks.evaluate(jobs);
                    for(int idx = 0; idx < 16; idx++) assertEquals(idx, res.get(idx));
                }));
            }
            for(var batch : batches) batch.get(10, TimeUnit.SECONDS);
        }
        assertEquals(512, completed.get());
        assertEquals(0, active.get());
        assertTrue(peak.get() <= ParallelTasks.parallelism());
    }
}
