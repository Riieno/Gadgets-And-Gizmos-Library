package com.rieno.gadgetsandgizmos.lib.util;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.LinkedHashSet;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

// Retain bounded change keys for independent readers on the owning game thread
public final class ValueChangeJournal<K>{
    // Bound retained history without retaining old value payloads
    private final int capacity;
    private Object[] keys;
    // Require a snapshot after an explicit reset or discarded history
    private long resetRevision;
    private long revision;

    // Set the maximum number of retained changes
    public ValueChangeJournal(int capacity){
        if(capacity < 1) throw new IllegalArgumentException("Change capacity must be positive");
        this.capacity = capacity;
    }

    // Record an added, changed, or removed key after its authoritative value changes
    public void record(K key){
        Objects.requireNonNull(key, "key");
        if(keys == null) keys = new Object[capacity];
        keys[(int)(++revision % capacity)] = key;
    }

    // Require every reader to replace its baseline after a lifecycle change
    public void reset(){
        if(keys != null) Arrays.fill(keys, null);
        resetRevision = ++revision;
    }

    // Get the latest published change revision
    public long revision(){ return revision; }

    // Coalesce pending keys without consuming another reader's changes
    @SuppressWarnings("unchecked")
    public Changes<K> changesSince(long prev){
        boolean full = prev < resetRevision || prev > revision || prev < revision - capacity;
        if(full) return new Changes<>(revision, true, Set.of());
        Set<K> changed = new LinkedHashSet<>();
        for(long idx = prev + 1; idx <= revision; idx++) changed.add((K)keys[(int)(idx % capacity)]);
        return new Changes<>(revision, false, changed);
    }

    // Carry detached change keys and indicate when the reader needs a full baseline
    public record Changes<K>(long revision, boolean full, Set<K> keys){
        // Keep one reader from changing another reader's pending keys
        public Changes{ keys = Set.copyOf(keys); }
    }
}
