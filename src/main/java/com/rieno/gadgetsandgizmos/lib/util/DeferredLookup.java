package com.rieno.gadgetsandgizmos.lib.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

// Retain in-flight lookups and bounded completed results for one owning game thread
public final class DeferredLookup<K, V>{
    private final Map<K, Entry<V>> entries = new LinkedHashMap<>(16, 0.75F, true);
    private final Map<Object, K> pendingRequests = new LinkedHashMap<>();
    private final int capacity;
    private final long lifetime;
    private final ToLongFunction<V> retention;

    public DeferredLookup(int capacity, long lifetime){
        this(capacity, lifetime, null);
    }

    // Keep successful results longer than transient unavailable results
    public DeferredLookup(int capacity, long lifetime, ToLongFunction<V> retention){
        if(capacity < 1 || lifetime < 1L) throw new IllegalArgumentException("Lookup cache limits must be positive");
        this.capacity = capacity;
        this.lifetime = lifetime;
        this.retention = retention;
    }

    // Poll a cached result without blocking or restarting an unfinished search
    public V get(DeferredWorkScheduler scheduler, K key, Supplier<V> task){
        Entry<V> entry = entries.get(key);
        if(entry != null && entry.result.isDone() && (entry.result.isCancelled()
                || entry.expires != Long.MAX_VALUE && scheduler.ticks() >= entry.expires
                        && !pendingRequests.containsValue(key))){
            entries.remove(key);
            entry = null;
        }
        if(entry == null){
            if(entries.size() >= capacity){
                var oldest = entries.entrySet().stream().filter(val -> val.getValue().result.isDone()
                        && !pendingRequests.containsValue(val.getKey()))
                        .findFirst().orElse(null);
                if(oldest == null) oldest = entries.entrySet().stream()
                        .filter(val -> val.getValue().result.isDone()).findFirst().orElse(null);
                if(oldest == null) throw new DeferredWorkScheduler.Pending();
                K evicted = oldest.getKey();
                entries.remove(evicted);
                pendingRequests.values().removeIf(val -> java.util.Objects.equals(val, evicted));
            }
            entry = new Entry<>(scheduler.submit(task));
            entries.put(key, entry);
        }
        if(!entry.result.isDone()) throw new DeferredWorkScheduler.Pending();
        return completed(scheduler, entry);
    }

    // Finish the original snapshot even when unrelated live stock changes while the request is pending
    public V getForRequest(DeferredWorkScheduler scheduler, Object requestId, K key, Supplier<V> task){
        K active = pendingRequests.get(requestId);
        if(active != null){
            Entry<V> entry = entries.get(active);
            if(entry != null && !entry.result.isCancelled()){
                if(!entry.result.isDone()) throw new DeferredWorkScheduler.Pending();
                pendingRequests.remove(requestId);
                return completed(scheduler, entry);
            }
            pendingRequests.remove(requestId);
        }
        if(pendingRequests.size() >= capacity){
            var oldest = pendingRequests.entrySet().stream().filter(val -> {
                Entry<V> entry = entries.get(val.getValue());
                return entry == null || entry.result.isDone();
            }).findFirst().orElse(null);
            if(oldest == null) throw new DeferredWorkScheduler.Pending();
            pendingRequests.remove(oldest.getKey());
        }
        try{ return get(scheduler, key, task); }
        catch(DeferredWorkScheduler.Pending pending){
            if(entries.containsKey(key)) pendingRequests.put(requestId, key);
            throw pending;
        }
    }

    // Retry a rejected cached result while preserving the new attempt across pending polls
    public V refreshForRequest(DeferredWorkScheduler scheduler, Object requestId, K key, Supplier<V> task){
        if(!pendingRequests.containsKey(requestId)){
            Entry<V> entry = entries.get(key);
            if(entry != null && entry.result.isDone()) entries.remove(key);
        }
        return getForRequest(scheduler, requestId, key, task);
    }

    // Release request identities while leaving shared cached computations available to other callers
    public void forgetRequests(Predicate<Object> matching){
        pendingRequests.keySet().removeIf(matching);
    }

    // Invalidate results and cancel work that no longer belongs to the consumer
    public void clear(){
        entries.values().forEach(entry -> entry.result.cancel(false));
        entries.clear();
        pendingRequests.clear();
    }

    private V completed(DeferredWorkScheduler scheduler, Entry<V> entry){
        if(entry.expires != Long.MAX_VALUE) return entry.result.join();
        entry.expires = scheduler.ticks() + lifetime;
        V res = entry.result.join();
        if(retention != null) entry.expires = scheduler.ticks() + Math.max(1L, retention.applyAsLong(res));
        return res;
    }

    private static final class Entry<V>{
        private final CompletableFuture<V> result;
        private long expires = Long.MAX_VALUE;
        private Entry(CompletableFuture<V> result){ this.result = result; }
    }
}
