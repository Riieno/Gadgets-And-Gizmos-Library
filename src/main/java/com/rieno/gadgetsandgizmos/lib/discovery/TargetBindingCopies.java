package com.rieno.gadgetsandgizmos.lib.discovery;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.BinaryOperator;
import java.util.function.Function;

// Copy immutable bindings between independent owners while retaining destination order and metadata
public final class TargetBindingCopies{
    private TargetBindingCopies(){}

    // Combine matching bindings with the destination passed first; never mutate either collection
    public static <T, K> List<T> merge(Collection<T> destination, Collection<T> source,
                                       Function<T, K> identity, BinaryOperator<T> combine){
        Objects.requireNonNull(identity);
        Objects.requireNonNull(combine);
        LinkedHashMap<K, T> result = new LinkedHashMap<>();
        append(result, destination, identity, combine);
        append(result, source, identity, combine);
        return List.copyOf(result.values());
    }

    private static <T, K> void append(LinkedHashMap<K, T> result, Collection<T> entries,
                                     Function<T, K> identity, BinaryOperator<T> combine){
        if(entries == null) return;
        for(T entry : entries){
            if(entry == null) continue;
            K key = Objects.requireNonNull(identity.apply(entry));
            result.merge(key, entry, (saved, incoming) -> Objects.requireNonNull(combine.apply(saved, incoming)));
        }
    }
}
