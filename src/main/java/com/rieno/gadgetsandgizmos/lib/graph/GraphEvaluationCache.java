package com.rieno.gadgetsandgizmos.lib.graph;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

// Discard evaluated ports when execution changes its player or other evaluation context
public final class GraphEvaluationCache<K, V> extends AbstractMap<K, V>{
    private final Map<K, V> values = new HashMap<>();
    private final Supplier<?> context;
    private Object sampledContext;

    public GraphEvaluationCache(Supplier<?> context){
        this.context = Objects.requireNonNull(context);
    }

    private Map<K, V> current(){
        Object next = context.get();
        if(!Objects.equals(sampledContext, next)){
            values.clear();
            sampledContext = next;
        }
        return values;
    }

    @Override public V get(Object key){ return current().get(key); }
    @Override public boolean containsKey(Object key){ return current().containsKey(key); }
    @Override public V put(K key, V value){ return current().put(key, value); }
    @Override public V remove(Object key){ return current().remove(key); }
    @Override public void clear(){ current().clear(); }
    @Override public Set<Entry<K, V>> entrySet(){ return current().entrySet(); }
}
