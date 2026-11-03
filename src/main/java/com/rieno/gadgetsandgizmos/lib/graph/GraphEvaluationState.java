package com.rieno.gadgetsandgizmos.lib.graph;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

// Isolate diagnostic evaluation writes from the host's runtime state on its owning thread
public final class GraphEvaluationState<V> extends AbstractMap<String, V>{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        DEFAULTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private final Map<String, V> runtime;
    private Map<String, V> evaluation;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                       FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    public GraphEvaluationState(Map<String, V> runtime){ this.runtime = runtime; }

    public boolean evaluating(){ return evaluation != null; }

    public Map<String, V> snapshot(UnaryOperator<V> copy){
        Map<String, V> values = new LinkedHashMap<>();
        current().forEach((key, val) -> values.put(key, copy.apply(val)));
        return values;
    }

    // Borrow untouched state and detach only keys read or written by this evaluation
    public Map<String, V> fork(UnaryOperator<V> copy){
        Map<String, V> source = current();
        return new AbstractMap<>(){
            private final Map<String, V> changes = new LinkedHashMap<>();
            private final Set<String> removed = new LinkedHashSet<>();
            private boolean cleared;

            @Override public boolean containsKey(Object key){
                return changes.containsKey(key) || !cleared && !removed.contains(key) && source.containsKey(key);
            }
            @Override public V get(Object key){
                if(changes.containsKey(key)) return changes.get(key);
                if(!containsKey(key)) return null;
                V val = copy.apply(source.get(key));
                changes.put((String)key, val);
                return val;
            }
            @Override public V put(String key, V val){
                V prev = get(key);
                removed.remove(key);
                changes.put(key, val);
                return prev;
            }
            @Override public V remove(Object key){
                V prev = get(key);
                changes.remove(key);
                removed.add((String)key);
                return prev;
            }
            @Override public void clear(){
                cleared = true;
                changes.clear();
                removed.clear();
            }
            @Override public Set<Entry<String, V>> entrySet(){
                Set<String> keys = new LinkedHashSet<>();
                if(!cleared) keys.addAll(source.keySet());
                keys.removeAll(removed);
                keys.addAll(changes.keySet());
                return new AbstractSet<>(){
                    @Override public int size(){ return keys.size(); }
                    @Override public Iterator<Entry<String, V>> iterator(){
                        Iterator<String> iterator = keys.iterator();
                        return new Iterator<>(){
                            private String key;
                            @Override public boolean hasNext(){ return iterator.hasNext(); }
                            @Override public Entry<String, V> next(){
                                key = iterator.next();
                                return new SimpleImmutableEntry<>(key, get(key));
                            }
                            @Override public void remove(){
                                if(key == null) throw new IllegalStateException();
                                removed.add(key);
                                changes.remove(key);
                                iterator.remove();
                                key = null;
                            }
                        };
                    }
                };
            }
        };
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
