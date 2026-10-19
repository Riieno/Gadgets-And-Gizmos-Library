package com.rieno.gadgetsandgizmos.lib.worker;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.util.DeferredWorkScheduler;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Retain recipe output and ingredient links across worker planning requests
public final class WorkerRecipeIndex implements WorkerRecipeSource{
    private final List<WorkerRecipeDefinition> definitions;
    private final Map<WorkerResourceKey, List<WorkerRecipeDefinition>> byOutput;
    private final Map<ResourceLocation, List<WorkerRecipeDefinition>> byRecipeId;
    private final WorkerRecipeSource source;
    private volatile WorkerRecipeRelationships relationships;
    private final Map<WorkerResourceKey, List<WorkerRecipeDefinition>> resolved = new LinkedHashMap<>();
    private final Map<WorkerResourceKey, List<WorkerRecipeDefinition>> dependencies = new LinkedHashMap<>();
    private final Map<WorkerResourceKey, Set<WorkerResourceKey>> relevantResources = new LinkedHashMap<>();

    // Index each output once while preserving deterministic recipe preference
    public WorkerRecipeIndex(List<WorkerRecipeDefinition> definitions){
        this(definitions, null);
    }

    // Keep demand-driven searches separate from complete immutable recipe indexes
    WorkerRecipeIndex(List<WorkerRecipeDefinition> definitions, WorkerRecipeSource source){
        this.source = source;
        this.definitions = List.copyOf(definitions);
        Map<WorkerResourceKey, List<WorkerRecipeDefinition>> grouped = new LinkedHashMap<>();
        for(WorkerRecipeDefinition def : this.definitions){
            DeferredWorkScheduler.checkpoint();
            grouped.computeIfAbsent(def.result(), key -> new ArrayList<>()).add(def);
        }
        Map<WorkerResourceKey, List<WorkerRecipeDefinition>> indexed = new LinkedHashMap<>();
        grouped.forEach((key, value) -> {
            value.sort(Comparator.comparing(def -> def.recipeId().toString()));
            indexed.put(key, List.copyOf(value));
        });
        byOutput = Map.copyOf(indexed);
        Map<ResourceLocation, List<WorkerRecipeDefinition>> ids = new LinkedHashMap<>();
        for(WorkerRecipeDefinition def : this.definitions)
            ids.computeIfAbsent(def.recipeId(), key -> new ArrayList<>()).add(def);
        Map<ResourceLocation, List<WorkerRecipeDefinition>> resolved = new LinkedHashMap<>();
        ids.forEach((key, val) -> resolved.put(key, List.copyOf(val)));
        byRecipeId = Map.copyOf(resolved);
    }

    public List<WorkerRecipeDefinition> definitions(){
        return definitions;
    }

    // Retain one compact relationship map for every worker using this recipe generation
    @Override public WorkerRecipeRelationships relationships(Collection<WorkerResourceKey> outputs){
        if(source != null){
            if(relationships == null) relationships = source.relationships(outputs);
            return relationships;
        }
        WorkerRecipeRelationships ready = relationships;
        if(ready != null) return ready;
        WorkerRecipeRelationships res = new WorkerRecipeRelationships(definitions);
        synchronized(this){
            if(relationships == null) relationships = res;
            return relationships;
        }
    }

    public List<WorkerRecipeDefinition> producing(WorkerResourceKey result){
        if(source != null && relationships != null) return relationships.producing(result);
        if(source != null) return resolved.computeIfAbsent(result, source::producing);
        return byOutput.getOrDefault(result, List.of());
    }

    // Resolve machine variants of one saved recipe without walking its dependency graph
    public List<WorkerRecipeDefinition> recipes(ResourceLocation recipeId){
        if(source != null) return resolved.values().stream().flatMap(List::stream)
                .filter(def -> def.recipeId().equals(recipeId)).distinct().toList();
        return byRecipeId.getOrDefault(recipeId, List.of());
    }

    // Find recipes that accept this item or fluid through any resolved ingredient alternative
    public List<WorkerRecipeDefinition> consuming(WorkerResourceKey resource){
        return relationships(List.of()).consuming(resource);
    }

    // Follow ingredient alternatives only from requested outputs
    public List<WorkerRecipeDefinition> dependencies(Collection<WorkerResourceKey> outputs){
        Set<WorkerRecipeDefinition> found = new LinkedHashSet<>();
        for(WorkerResourceKey output : outputs) found.addAll(dependencies(output));
        return List.copyOf(found);
    }

    public List<WorkerRecipeDefinition> dependencies(WorkerResourceKey output){
        synchronized(dependencies){
            List<WorkerRecipeDefinition> ready = dependencies.get(output);
            if(ready != null) return ready;
        }
        Set<WorkerRecipeDefinition> found = new LinkedHashSet<>();
        Set<WorkerResourceKey> visited = new HashSet<>();
        ArrayDeque<WorkerResourceKey> pending = new ArrayDeque<>();
        pending.add(output);
        visited.add(output);
        while(!pending.isEmpty()){
            DeferredWorkScheduler.checkpoint();
            WorkerResourceKey resource = pending.removeLast();
            for(WorkerRecipeDefinition def : producing(resource)){
                found.add(def);
                for(WorkerRecipeDefinition.Ingredient ingredient : def.ingredients()){
                    for(WorkerResourceKey alternative : ingredient.alternatives())
                        if(visited.add(alternative)) pending.add(alternative);
                }
            }
        }
        List<WorkerRecipeDefinition> resolved = List.copyOf(found);
        synchronized(dependencies){
            List<WorkerRecipeDefinition> ready = dependencies.get(output);
            if(ready != null) return ready;
            if(dependencies.size() >= 128) dependencies.remove(dependencies.keySet().iterator().next());
            dependencies.put(output, resolved);
        }
        return resolved;
    }

    // Identify stocked resources that can affect a requested recipe chain
    public Set<WorkerResourceKey> relevantResources(WorkerResourceKey output){
        synchronized(relevantResources){
            Set<WorkerResourceKey> ready = relevantResources.get(output);
            if(ready != null) return ready;
        }
        Set<WorkerResourceKey> relevant = new HashSet<>();
        relevant.add(output);
        for(WorkerRecipeDefinition def : dependencies(output)){
            for(WorkerRecipeDefinition.Ingredient ingredient : def.ingredients())
                relevant.addAll(ingredient.alternatives());
        }
        Set<WorkerResourceKey> resolved = Set.copyOf(relevant);
        synchronized(relevantResources){
            Set<WorkerResourceKey> ready = relevantResources.get(output);
            if(ready != null) return ready;
            if(relevantResources.size() >= 128) relevantResources.remove(relevantResources.keySet().iterator().next());
            relevantResources.put(output, resolved);
        }
        return resolved;
    }
}
