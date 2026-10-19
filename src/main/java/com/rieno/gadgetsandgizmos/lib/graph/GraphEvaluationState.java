package com.rieno.gadgetsandgizmos.lib.graph;

import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

// Isolate diagnostic evaluation writes from the host's runtime state on its owning thread
public final class GraphEvaluationState<V> extends AbstractMap<String, V>{
    private final Map<String, V> runtime;
    private Map<String, V> evaluation;

    public GraphEvaluationState(Map<String, V> runtime){ this.runtime = runtime; }

    public boolean evaluating(){ return evaluation != null; }

    public Map<String, V> snapshot(UnaryOperator<V> copy){
        Map<String, V> values = new LinkedHashMap<>();
        current().forEach((key, val) -> values.put(key, copy.apply(val)));
        return values;
    }

    // Nested samples restore their caller's state even when evaluation fails
    public <T> T evaluate(Map<String, V> values, Supplier<T> sample){
        Map<String, V> prev = evaluation;
        evaluation = values;
        try{ return sample.get(); }
        finally{ evaluation = prev; }
    }

    private Map<String, V> current(){ return evaluation == null ? runtime : evaluation; }
    @Override public V get(Object key){ return current().get(key); }
    @Override public boolean containsKey(Object key){ return current().containsKey(key); }
    @Override public V put(String key, V val){ return current().put(key, val); }
    @Override public V remove(Object key){ return current().remove(key); }
    @Override public void clear(){ current().clear(); }
    @Override public Set<Entry<String, V>> entrySet(){ return current().entrySet(); }
}
