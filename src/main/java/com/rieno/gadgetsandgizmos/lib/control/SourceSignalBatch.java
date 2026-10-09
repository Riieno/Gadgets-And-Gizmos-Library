package com.rieno.gadgetsandgizmos.lib.control;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// Apply final source values together before notifying effective signal changes
public final class SourceSignalBatch<K>{
    private final int maximum;
    private final Map<K, Map<String, Integer>> updates = new LinkedHashMap<>();

    public SourceSignalBatch(int maximum){
        if(maximum < 1) throw new IllegalArgumentException("Signal maximum must be positive");
        this.maximum = maximum;
    }

    // Later writes to the same source replace earlier writes in this batch
    public void set(K key, String source, int strength){
        Objects.requireNonNull(key);
        if(source == null || source.isBlank()) return;
        updates.computeIfAbsent(key, ignored -> new LinkedHashMap<>())
                .put(source, Math.clamp(strength, 0, maximum));
    }

    // Mutate the owning store and return only changes to the maximum source value
    public List<Change<K>> applyTo(Map<K, Map<String, Integer>> signals){
        List<Change<K>> changes = new ArrayList<>();
        updates.forEach((key, sources) -> {
            Map<String, Integer> current = signals.get(key);
            int prev = maximum(current);
            for(var entry : sources.entrySet()){
                if(entry.getValue() == 0){
                    if(current != null) current.remove(entry.getKey());
                }else{
                    if(current == null){
                        current = new HashMap<>();
                        signals.put(key, current);
                    }
                    current.put(entry.getKey(), entry.getValue());
                }
            }
            int next = maximum(current);
            if(current != null && current.isEmpty()) signals.remove(key);
            if(prev != next) changes.add(new Change<>(key, prev, next));
        });
        updates.clear();
        return List.copyOf(changes);
    }

    private static int maximum(Map<String, Integer> sources){
        int res = 0;
        if(sources != null){
            for(int val : sources.values()) res = Math.max(res, val);
        }
        return res;
    }

    public record Change<K>(K key, int previousStrength, int strength){}
}
