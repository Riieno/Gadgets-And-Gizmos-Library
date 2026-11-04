package com.rieno.gadgetsandgizmos.lib.graph;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// Recover unambiguous legacy overlays while retaining the authoritative shared state
public final class GraphSharedState{
    private GraphSharedState(){}

    public static <V> Map<String, V> mergeLegacy(Map<String, V> shared,
                                                Collection<? extends Map<String, V>> overlays){
        Map<String, V> values = new LinkedHashMap<>();
        Set<String> conflicting = new HashSet<>();
        for(Map<String, V> overlay : overlays){
            overlay.forEach((key, val) -> {
                if(values.containsKey(key) && !Objects.equals(values.get(key), val)) conflicting.add(key);
                else values.put(key, val);
            });
        }
        conflicting.forEach(values::remove);
        values.putAll(shared);
        return values;
    }
}
