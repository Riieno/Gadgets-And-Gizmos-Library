package com.rieno.gadgetsandgizmos.lib.util;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

// Spread live world queries across ticks while searching detached data away from the server thread
@EventBusSubscriber(modid = "gadgetsngizmos")
public final class DeferredWorkScheduler implements AutoCloseable{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<MinecraftServer, DeferredWorkScheduler> SERVERS = new IdentityHashMap<>();
    private static final ThreadLocal<Work> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<DeferredWorkScheduler> OWNER_QUERY = new ThreadLocal<>();
    private static final int MAX_JOBS = 64;
    private static final int MAX_QUERIES = 64;
    private static final long TICK_BUDGET = 2_000_000L;
    private final Semaphore computing = new Semaphore(Math.max(1,
            Math.min(2, Runtime.getRuntime().availableProcessors() / 2)), true);
    private final ExecutorService executor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("G&G lookup-", 0).factory());
    private final ConcurrentLinkedQueue<Query<?>> queries = new ConcurrentLinkedQueue<>();
    private final Set<CompletableFuture<?>> jobs = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;
    private long ticks;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Share one live-query budget between all consumers on a server
    public static synchronized DeferredWorkScheduler forServer(MinecraftServer server){
        if(server == null) throw new IllegalArgumentException("Deferred work needs a server");
        return SERVERS.computeIfAbsent(server, key -> new DeferredWorkScheduler());
    }

    // Submit pure computation without waiting on the caller's game thread
    public synchronized <T> CompletableFuture<T> submit(Supplier<T> task){
        if(closed) throw new CancellationException("The server lookup scheduler has stopped");
        if(jobs.size() >= MAX_JOBS) throw new Pending();
        CompletableFuture<T> res = new CompletableFuture<>();
        jobs.add(res);
        executor.execute(() -> {
            Work work = new Work(this, res);
            CURRENT.set(work);
            try{
                acquire(work);
                if(!res.isCancelled()) res.complete(task.get());
            }catch(Throwable ex){
                res.completeExceptionally(ex);
            }finally{
                release(work);
                CURRENT.remove();
                jobs.remove(res);
            }
        });
        return res;
    }

    // Read Minecraft state on the owning thread and wait only on a background worker
    public <T> T onOwnerThread(Supplier<T> task){
        if(OWNER_QUERY.get() == this) return task.get();
        Work work = CURRENT.get();
        if(work == null || work.scheduler != this)
            throw new IllegalStateException("Owner queries must come from this scheduler's background work");
        checkCancelled(work);
        Query<T> query = new Query<>(task, work.result);
        queries.add(query);
        return await(work, query.result);
    }

    // Queue independent live queries together while retaining the shared per-tick admission budget
    public static <T> List<T> queryTogether(List<? extends Supplier<T>> tasks){
        Work work = CURRENT.get();
        if(work == null) return tasks.stream().map(Supplier::get).toList();
        checkCancelled(work);
        List<T> values = new ArrayList<>();
        for(int start = 0; start < tasks.size(); start += MAX_QUERIES){
            checkCancelled(work);
            List<Query<T>> batch = new ArrayList<>();
            for(int idx = start; idx < Math.min(start + MAX_QUERIES, tasks.size()); idx++){
                Query<T> query = new Query<>(tasks.get(idx), work.result);
                batch.add(query);
                work.scheduler.queries.add(query);
            }
            for(var query : batch) values.add(work.scheduler.await(work, query.result));
        }
        return List.copyOf(values);
    }

    // Check independent detached indexes together without occupying a CPU slot while waiting for children
    public static <T> List<T> parallel(List<? extends Supplier<T>> tasks){
        Work work = CURRENT.get();
        if(work == null || tasks.size() < 2) return tasks.stream().map(Supplier::get).toList();
        List<T> values = new ArrayList<>();
        for(int start = 0; start < tasks.size(); start += 2){
            List<CompletableFuture<T>> batch = new ArrayList<>();
            try{
                for(int idx = start; idx < Math.min(start + 2, tasks.size()); idx++){
                    try{ batch.add(work.scheduler.submit(tasks.get(idx))); }
                    catch(Pending full){ batch.add(CompletableFuture.completedFuture(tasks.get(idx).get())); }
                }
                for(var child : batch) values.add(work.scheduler.await(work, child));
            }finally{
                for(var child : batch) if(!child.isDone()) child.cancel(false);
            }
        }
        return List.copyOf(values);
    }

    private <T> T await(Work work, CompletableFuture<T> result){
        release(work);
        try{
            return result.get();
        }catch(InterruptedException ex){
            Thread.currentThread().interrupt();
            throw new CancellationException("Lookup interrupted");
        }catch(java.util.concurrent.ExecutionException ex){
            throw new java.util.concurrent.CompletionException(ex.getCause());
        }finally{
            acquire(work);
        }
    }

    // Yield a long graph search without discarding its queued work
    public static void checkpoint(){
        if(Thread.currentThread().isInterrupted()) throw new CancellationException("Lookup interrupted");
        Work work = CURRENT.get();
        if(work == null) return;
        checkCancelled(work);
        if(++work.operations < 256) return;
        work.operations = 0;
        if(System.nanoTime() < work.deadline) return;
        work.scheduler.release(work);
        LockSupport.parkNanos(500_000L);
        work.scheduler.acquire(work);
    }

    public long ticks(){ return ticks; }

    // Process a bounded share of queued live queries on the server thread
    public void tick(){
        ticks++;
        long deadline = System.nanoTime() + TICK_BUDGET;
        int count = 0;
        while(count < MAX_QUERIES && System.nanoTime() < deadline){
            Query<?> query = queries.poll();
            if(query == null){
                if(jobs.isEmpty()) break;
                java.util.concurrent.locks.LockSupport.parkNanos(10_000L);
                continue;
            }
            count++;
            OWNER_QUERY.set(this);
            try{ query.run(); }
            finally{ OWNER_QUERY.remove(); }
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post evt){
        DeferredWorkScheduler scheduler;
        synchronized(DeferredWorkScheduler.class){ scheduler = SERVERS.get(evt.getServer()); }
        if(scheduler != null) scheduler.tick();
    }

    @SubscribeEvent
    public static synchronized void stopped(ServerStoppedEvent evt){
        DeferredWorkScheduler scheduler = SERVERS.remove(evt.getServer());
        if(scheduler != null) scheduler.close();
    }

    // Release queued tasks and any background thread waiting for an unloaded world
    @Override public void close(){
        closed = true;
        synchronized(DeferredWorkScheduler.class){ SERVERS.values().removeIf(val -> val == this); }
        for(CompletableFuture<?> job : new ArrayList<>(jobs)) job.cancel(false);
        executor.shutdownNow();
        Query<?> query;
        while((query = queries.poll()) != null) query.result.cancel(false);
        jobs.clear();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static void checkCancelled(Work work){
        if(work.scheduler.closed || work.result.isCancelled() || Thread.currentThread().isInterrupted())
            throw new CancellationException("Lookup cancelled");
    }

    private void acquire(Work work){
        checkCancelled(work);
        try{ computing.acquire(); }
        catch(InterruptedException ex){
            Thread.currentThread().interrupt();
            throw new CancellationException("Lookup interrupted");
        }
        work.permit = true;
        work.deadline = System.nanoTime() + TICK_BUDGET;
        checkCancelled(work);
    }

    private void release(Work work){
        if(!work.permit) return;
        work.permit = false;
        computing.release();
    }

    private static final class Work{
        private final DeferredWorkScheduler scheduler;
        private final CompletableFuture<?> result;
        private int operations;
        private boolean permit;
        private long deadline;
        private Work(DeferredWorkScheduler scheduler, CompletableFuture<?> result){
            this.scheduler = scheduler;
            this.result = result;
        }
    }

    private record Query<T>(Supplier<T> task, CompletableFuture<?> job, CompletableFuture<T> result){
        private Query(Supplier<T> task, CompletableFuture<?> job){ this(task, job, new CompletableFuture<>()); }
        private void run(){
            if(job.isDone()){
                result.cancel(false);
                return;
            }
            try{ result.complete(task.get()); }
            catch(Throwable ex){ result.completeExceptionally(ex); }
        }
    }

    // Return control to a caller that will poll again on a later tick
    public static final class Pending extends RuntimeException{
        public Pending(){ super(null, null, false, false); }
    }
}
