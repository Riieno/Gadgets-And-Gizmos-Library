package com.rieno.gadgetsandgizmos.lib.util;

import java.util.Objects;
import java.util.function.Supplier;

// Retain one detached snapshot until its source identity or revision changes on the owning thread
public final class RevisionSnapshotCache<T>{
    private Object source;
    private long revision;
    private T val;
    private boolean present;

    // Include source replacement even when the new document retains the same saved revision
    public T get(Object source, long revision, Supplier<? extends T> loader){
        if(present && this.source == source && this.revision == revision) return val;
        T next = Objects.requireNonNull(loader, "loader").get();
        this.source = source;
        this.revision = revision;
        val = next;
        present = true;
        return next;
    }

    // Release the snapshot when its host or world is unloaded
    public void invalidate(){
        source = null;
        val = null;
        present = false;
    }
}
