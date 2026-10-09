package com.rieno.gadgetsandgizmos.lib.util;

import java.util.Objects;
import java.util.function.Supplier;

// Reuse one snapshot on the owning game thread until its tick or input key changes
public final class TickSnapshotCache<T>{
    private Object key;
    private long tick;
    private T val;
    private boolean present;

    // Include every input that can change during the tick in the key
    public T get(Object key, long tick, Supplier<? extends T> loader){
        if(present && this.tick == tick && Objects.equals(this.key, key)) return val;
        T next = Objects.requireNonNull(loader, "loader").get();
        this.key = key;
        this.tick = tick;
        val = next;
        present = true;
        return next;
    }

    // Release the current snapshot after an explicit state or lifecycle change
    public void invalidate(){
        key = null;
        val = null;
        present = false;
    }
}
