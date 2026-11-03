package com.rieno.gadgetsandgizmos.lib.graph;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

// Retain disabled saved nodes while rejecting new instances through any graph import or editor
public final class GraphNodeAvailability{
    // Keep graph comparisons on the static API
    private GraphNodeAvailability(){}

    // Compare scoped identities rather than allowing an equal node count to hide replacement
    public static boolean canReplace(CompoundTag prev, CompoundTag next, Predicate<String> disabled){
        Map<Key, Integer> before = new HashMap<>();
        Map<Key, Integer> after = new HashMap<>();
        collect(prev, List.of(), disabled, before);
        collect(next, List.of(), disabled, after);
        return after.entrySet().stream().allMatch(entry -> entry.getValue() <= before.getOrDefault(entry.getKey(), 0));
    }

    // Count root and nested function instances without collapsing duplicate identifiers
    private static void collect(CompoundTag graph, List<String> scope, Predicate<String> disabled, Map<Key, Integer> counts){
        for(Tag raw : graph.getList("Nodes", Tag.TAG_COMPOUND)){
            var node = (CompoundTag)raw;
            if(disabled.test(node.getString("Type")))
                counts.merge(new Key(scope, node.getString("Id"), node.getString("Type")), 1, Integer::sum);
        }
        for(Tag raw : graph.getList("Functions", Tag.TAG_COMPOUND)){
            var function = (CompoundTag)raw;
            var nested = new ArrayList<>(scope); nested.add(function.getString("Id"));
            collect(function, List.copyOf(nested), disabled, counts);
        }
    }

    // Keep function scope separate from user-authored node identifiers
    private record Key(List<String> scope, String id, String type){}
}
