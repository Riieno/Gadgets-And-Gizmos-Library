package com.rieno.gadgetsandgizmos.lib.graph;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

// Retain submitted data outputs independently of a host's personal UI state
public final class GraphInteractionOutputs<V>{
    private final Map<String, Submission<V>> outputs = new LinkedHashMap<>();

    // Publish the latest value and its optional interaction owner for a data port
    public void submit(String key, V val, UUID playerId){
        outputs.put(Objects.requireNonNull(key), new Submission<>(val, playerId));
    }

    // Read a submitted value, including an explicitly submitted empty value
    public V valueOrDefault(String key, V fallback){
        Submission<V> submission = outputs.get(key);
        return submission == null ? fallback : submission.value();
    }

    // Read the latest submission without inventing an interaction owner
    public Submission<V> submission(String key){ return outputs.get(key); }

    // Export submitted outputs for host-owned serialization
    public Map<String, Submission<V>> snapshot(){ return Map.copyOf(outputs); }

    // Drop outputs whose ports no longer exist in the host graph
    public void retainKeys(Predicate<String> valid){ outputs.keySet().removeIf(key -> !valid.test(key)); }

    public void clear(){ outputs.clear(); }

    public record Submission<V>(V value, UUID playerId){
        public Submission{ Objects.requireNonNull(value); }
    }
}
