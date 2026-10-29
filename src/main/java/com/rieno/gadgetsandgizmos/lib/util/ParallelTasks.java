package com.rieno.gadgetsandgizmos.lib.util;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

// Complete independent snapshot calculations in input order before returning to the owning thread
public final class ParallelTasks{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        CONSTANTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private static final int WORKERS = Math.min(2, Math.max(0, Runtime.getRuntime().availableProcessors() - 2));
    private static final ThreadLocal<Boolean> WORKER = ThreadLocal.withInitial(() -> false);
    private static final AtomicInteger NEXT_ID = new AtomicInteger();
    private static final ThreadPoolExecutor EXECUTOR = WORKERS == 0 ? null : new ThreadPoolExecutor(
            0, WORKERS, 30, TimeUnit.SECONDS, new SynchronousQueue<>(), task -> {
                Thread thread = new Thread(task, "gadgetsngizmos-compute-" + NEXT_ID.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            });

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private ParallelTasks(){}

    // Report the maximum shared workers; the calling thread also performs calculations
    public static int parallelism(){ return WORKERS; }

    // Jobs must use detached inputs and must not access live levels, rendering or shared mutable state
    public static <T> List<T> evaluate(List<? extends Supplier<? extends T>> jobs){
        return evaluate(jobs, EXECUTOR);
    }

    static <T> List<T> evaluate(List<? extends Supplier<? extends T>> jobs, java.util.concurrent.Executor executor){
        var inputs = List.copyOf(jobs);
        if(inputs.isEmpty()) return List.of();
        List<FutureTask<T>> pending = new ArrayList<>(inputs.size());
        for(var job : inputs) pending.add(new FutureTask<>(() -> job.get()));
        boolean parallel = executor != null && !WORKER.get() && pending.size() > 1;
        for(int idx = 1; idx < pending.size(); idx++){
            FutureTask<T> task = pending.get(idx);
            if(parallel){
                try{
                    executor.execute(() -> {
                        WORKER.set(true);
                        try{ task.run(); }finally{ WORKER.remove(); }
                    });
                    continue;
                }catch(RejectedExecutionException ignored){}
            }
            task.run();
        }
        pending.getFirst().run();
        List<T> res = new ArrayList<>(pending.size());
        Throwable failure = null;
        boolean interrupted = false;
        // Finish every accepted job, even when a sibling fails or the caller is interrupted
        for(FutureTask<T> task : pending){
            while(true){
                try{
                    res.add(task.get());
                    break;
                }catch(InterruptedException ignored){
                    interrupted = true;
                }catch(ExecutionException err){
                    if(failure == null) failure = err.getCause();
                    res.add(null);
                    break;
                }
            }
        }
        if(interrupted) Thread.currentThread().interrupt();
        if(failure instanceof Error err) throw err;
        if(failure instanceof RuntimeException err) throw err;
        if(failure != null) throw new IllegalStateException("Snapshot calculation failed", failure);
        return Collections.unmodifiableList(res);
    }
}
