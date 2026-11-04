package com.rieno.gadgetsandgizmos.lib.util;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

// Cache selected values and visit only changed keys between full baselines
public final class ValueProjection<K, V>{
    // Keep selection policy separate from the source's change history
    private final Predicate<K> included;
    private final Map<K, V> values = new LinkedHashMap<>();
    private final Map<K, V> view = Collections.unmodifiableMap(values);
    // Track this reader independently from every other projection
    private Object source;
    private long revision = Long.MIN_VALUE;

    // Select the keys that this presentation consumes
    public ValueProjection(Predicate<K> included){
        this.included = Objects.requireNonNull(included, "included");
    }

    // Get the last change revision applied to this projection
    public long revision(){ return revision; }

    // Borrow selected values until the next update on the owning thread
    public Map<K, V> values(){ return view; }

    // Apply source changes without scanning or copying unchanged values
    public ValueDelta<K, V> update(Object source, ValueChangeJournal.Changes<K> changes,
                                   Map<K, V> current){
        Objects.requireNonNull(source, "source");
        boolean full = this.source != source || changes.full();
        Map<K, V> changed = new LinkedHashMap<>();
        Set<K> removed = new LinkedHashSet<>();
        if(full){
            current.forEach((key, val) -> { if(included.test(key)) changed.put(key, val); });
        }else{
            for(K key : changes.keys()){
                if(!included.test(key)) continue;
                V val = current.get(key);
                if(val == null){
                    if(values.containsKey(key)) removed.add(key);
                }else if(!Objects.equals(values.get(key), val)) changed.put(key, val);
            }
        }
        ValueDelta<K, V> delta = new ValueDelta<>(full, changed, removed);
        delta.applyTo(values);
        this.source = source;
        revision = changes.revision();
        return delta;
    }
}
