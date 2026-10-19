package com.rieno.gadgetsandgizmos.lib.worker;

import java.util.HashMap;
import java.util.Map;

// Limit repeated planning attempts for routines whose current inputs cannot run
public final class WorkerRoutineRetryGate{
    private final Map<String, Long> nextAttempt = new HashMap<>();

    public boolean ready(String key, long tick){
        return key != null && tick >= nextAttempt.getOrDefault(key, Long.MIN_VALUE);
    }

    public void defer(String key, long tick, long delay){
        if(key == null) return;
        nextAttempt.put(key, tick + Math.max(1L, delay));
    }

    public void clear(String key){
        if(key != null) nextAttempt.remove(key);
    }

    public void clear(){
        nextAttempt.clear();
    }
}
