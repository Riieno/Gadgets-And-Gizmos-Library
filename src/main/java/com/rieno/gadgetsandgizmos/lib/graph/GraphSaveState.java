package com.rieno.gadgetsandgizmos.lib.graph;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

// Preserve server-owned values while replacing an edited graph on its owning thread
public final class GraphSaveState{
    private GraphSaveState(){}

    // Keep current values for surviving variables; new declarations retain their supplied defaults
    public static <V> void mergeVariables(Map<String, V> live, Map<String, V> saved, UnaryOperator<V> copy){
        saved.replaceAll((key, val) -> live.containsKey(key) ? copy.apply(live.get(key)) : val);
    }

    // Copy only state slots explicitly retained by the replacement graph
    public static <V> Map<String, V> retain(Map<String, V> values, Predicate<String> keep, UnaryOperator<V> copy){
        Map<String, V> retained = new LinkedHashMap<>();
        values.forEach((key, val) -> {
            if(keep.test(key)) retained.put(key, copy.apply(val));
        });
        return retained;
    }
}
