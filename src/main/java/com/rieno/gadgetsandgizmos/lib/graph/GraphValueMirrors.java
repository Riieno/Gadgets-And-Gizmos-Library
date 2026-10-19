package com.rieno.gadgetsandgizmos.lib.graph;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

// Combine sampled graph values with authoritative values recorded during execution
public final class GraphValueMirrors{
    private GraphValueMirrors(){}

    // Read retained values for host-selected keys without evaluating their preview fallback
    public static <V> V sample(Map<String, V> live, String key,
                               Predicate<String> authoritative, Supplier<V> fallback){
        if(live.containsKey(key) && authoritative.test(key)) return live.get(key);
        return fallback.get();
    }

    // Preserve executed values for selected ports while filling other ports from preview samples
    public static <V> Map<String, V> merge(Map<String, V> live, Map<String, V> sampled,
                                          Predicate<String> authoritative){
        Map<String, V> merged = new LinkedHashMap<>(live);
        sampled.forEach((key, val) -> {
            if(!live.containsKey(key) || !authoritative.test(key)) merged.put(key, val);
        });
        return merged;
    }
}
