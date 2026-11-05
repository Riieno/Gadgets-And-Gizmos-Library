package com.rieno.gadgetsandgizmos.lib.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// Preserve newer value updates while an older baseline is still being transferred
public final class VersionedValueOverlay<K, V>{
    // Borrow immutable values only while the owning reader is waiting for its baseline
    private Object source;
    private final Map<K, Change<V>> changes = new LinkedHashMap<>();

    // Keep the newest assignment or removal for each key within one source generation
    public void record(Object source, long revision, Map<K, V> values, Set<K> removed){
        if(!Objects.equals(this.source, source)){ clear(); this.source = source; }
        values.forEach((key, val) -> changes.put(key, new Change<>(revision, val, false)));
        removed.forEach(key -> changes.put(key, new Change<>(revision, null, true)));
    }

    // Fill unchanged baseline values and replay only updates newer than that baseline
    public Map<K, V> merge(Object source, long revision, Map<K, V> baseline){
        Map<K, V> res = new LinkedHashMap<>(baseline);
        if(!Objects.equals(this.source, source)) return res;
        changes.forEach((key, change) -> {
            if(change.revision <= revision) return;
            if(change.removed) res.remove(key);
            else res.put(key, change.val);
        });
        return res;
    }

    // Release pending values when the baseline arrives or its host is replaced
    public void clear(){ source = null; changes.clear(); }

    // Associate one final assignment with its publication revision
    private record Change<V>(long revision, V val, boolean removed){}
}
