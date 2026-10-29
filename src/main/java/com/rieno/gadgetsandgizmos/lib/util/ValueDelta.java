package com.rieno.gadgetsandgizmos.lib.util;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// Carry a detached full baseline or changed values and removals
public record ValueDelta<K, V>(boolean full, Map<K, V> values, Set<K> removed){
    // Detach containers while borrowing values that the owner treats as immutable
    public ValueDelta{
        values = Map.copyOf(values);
        removed = Set.copyOf(removed);
        if(values.keySet().stream().anyMatch(removed::contains)){
            throw new IllegalArgumentException("Changed and removed keys must not overlap");
        }
    }

    // Compare a selected snapshot with the last snapshot delivered to one reader
    public static <K, V> ValueDelta<K, V> between(Map<K, V> prev, Map<K, V> next){
        if(prev == null) return new ValueDelta<>(true, next, Set.of());
        Map<K, V> changed = new LinkedHashMap<>();
        next.forEach((key, val) -> {
            if(!Objects.equals(prev.get(key), val)) changed.put(key, val);
        });
        Set<K> removed = new LinkedHashSet<>(prev.keySet());
        removed.removeAll(next.keySet());
        return new ValueDelta<>(false, changed, removed);
    }

    // Check whether applying this update would preserve the current baseline
    public boolean isEmpty(){ return !full && values.isEmpty() && removed.isEmpty(); }

    // Replace or update a reader-owned cache on its owning thread
    public void applyTo(Map<K, V> target){
        if(full) target.clear();
        removed.forEach(target::remove);
        target.putAll(values);
    }
}
