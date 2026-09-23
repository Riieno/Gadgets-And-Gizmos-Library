package com.rieno.gadgetsandgizmos.lib.navigation;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

// Select one recurring job per interval without starving stable jobs when others change
public final class StaggeredWorkQueue<K> {
    private final ArrayDeque<K> pending = new ArrayDeque<>();
    private final Set<K> queued = new LinkedHashSet<>();
    private final int interval;
    private long lastTick = Long.MIN_VALUE;

    public StaggeredWorkQueue(int interval){
        this.interval = Math.max(1, interval);
    }

    public K next(Collection<K> active, long tick){
        if(lastTick != Long.MIN_VALUE && tick >= lastTick && tick - lastTick < interval) return null;
        lastTick = tick;
        Set<K> live = new LinkedHashSet<>(active);
        pending.removeIf(key -> !live.contains(key));
        queued.retainAll(live);
        for(K key : live) if(queued.add(key)) pending.addLast(key);
        K next = pending.pollFirst();
        if(next != null) pending.addLast(next);
        return next;
    }

    public void clear(){
        pending.clear();
        queued.clear();
        lastTick = Long.MIN_VALUE;
    }
}
