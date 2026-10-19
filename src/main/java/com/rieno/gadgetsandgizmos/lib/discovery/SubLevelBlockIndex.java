package com.rieno.gadgetsandgizmos.lib.discovery;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

// Group immutable references by their sublevel and block position
public final class SubLevelBlockIndex<T>{
    private final Map<Address, List<T>> entries;

    public SubLevelBlockIndex(Collection<T> values, Function<T, UUID> subLevelIds,
            Function<T, BlockPos> positions){
        Map<Address, List<T>> grouped = new HashMap<>();
        for(T val : values){
            Address key = new Address(subLevelIds.apply(val), positions.apply(val).immutable());
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(val);
        }
        grouped.replaceAll((key, vals) -> List.copyOf(vals));
        entries = Map.copyOf(grouped);
    }

    // Retain all faces and adapters at an address for the caller's exact matching rules
    public List<T> at(UUID subLevelId, BlockPos pos){
        return entries.getOrDefault(new Address(subLevelId, pos), List.of());
    }

    private record Address(UUID subLevelId, BlockPos pos){}

    // Retain bounded indexes of immutable collections on the owning thread
    public static final class Cache<T>{
        private final int limit;
        private final Function<T, UUID> subLevelIds;
        private final Function<T, BlockPos> positions;
        private final Map<Collection<T>, SubLevelBlockIndex<T>> indexes = new IdentityHashMap<>();

        public Cache(int limit, Function<T, UUID> subLevelIds, Function<T, BlockPos> positions){
            if(limit < 1) throw new IllegalArgumentException("Index limit must be positive");
            this.limit = limit;
            this.subLevelIds = subLevelIds;
            this.positions = positions;
        }

        // The collection must remain immutable until the cache is cleared
        public SubLevelBlockIndex<T> get(Collection<T> values){
            SubLevelBlockIndex<T> cached = indexes.get(values);
            if(cached != null) return cached;
            if(indexes.size() >= limit) indexes.clear();
            SubLevelBlockIndex<T> res = new SubLevelBlockIndex<>(values, subLevelIds, positions);
            indexes.put(values, res);
            return res;
        }

        public void clear(){ indexes.clear(); }
    }
}
