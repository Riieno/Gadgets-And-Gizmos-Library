package com.rieno.gadgetsandgizmos.lib.worker;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Retain recipe output and ingredient links across worker planning requests
public final class WorkerRecipeIndex{
    private final List<WorkerRecipeDefinition> definitions;
    private final Map<WorkerResourceKey, List<WorkerRecipeDefinition>> byOutput;
    private final Map<WorkerResourceKey, List<WorkerRecipeDefinition>> byIngredient;
    private final Map<WorkerResourceKey, List<WorkerRecipeDefinition>> dependencies = new HashMap<>();
    private final Map<WorkerResourceKey, Set<WorkerResourceKey>> relevantResources = new HashMap<>();

    // Index each output once while preserving deterministic recipe preference
    public WorkerRecipeIndex(List<WorkerRecipeDefinition> definitions){
        this.definitions = List.copyOf(definitions);
        Map<WorkerResourceKey, List<WorkerRecipeDefinition>> grouped = new LinkedHashMap<>();
        this.definitions.stream().sorted(Comparator.comparing(def -> def.recipeId().toString())).forEach(def ->
                grouped.computeIfAbsent(def.result(), key -> new ArrayList<>()).add(def));
        Map<WorkerResourceKey, List<WorkerRecipeDefinition>> indexed = new LinkedHashMap<>();
        grouped.forEach((key, value) -> indexed.put(key, List.copyOf(value)));
        byOutput = Map.copyOf(indexed);
        Map<WorkerResourceKey, Set<WorkerRecipeDefinition>> consuming = new LinkedHashMap<>();
        for(WorkerRecipeDefinition def : this.definitions){
            for(WorkerRecipeDefinition.Ingredient ingredient : def.ingredients()){
                for(WorkerResourceKey resource : ingredient.alternatives()){
                    consuming.computeIfAbsent(resource, key -> new LinkedHashSet<>()).add(def);
                }
            }
        }
        Map<WorkerResourceKey, List<WorkerRecipeDefinition>> reverse = new LinkedHashMap<>();
        consuming.forEach((key, value) -> reverse.put(key, List.copyOf(value)));
        byIngredient = Map.copyOf(reverse);
    }

    public List<WorkerRecipeDefinition> definitions(){
        return definitions;
    }

    public List<WorkerRecipeDefinition> producing(WorkerResourceKey result){
        return byOutput.getOrDefault(result, List.of());
    }

    // Find recipes that accept this item or fluid through any resolved ingredient alternative
    public List<WorkerRecipeDefinition> consuming(WorkerResourceKey resource){
        return byIngredient.getOrDefault(resource, List.of());
    }

    // Follow ingredient alternatives only from requested outputs
    public List<WorkerRecipeDefinition> dependencies(Collection<WorkerResourceKey> outputs){
        Set<WorkerRecipeDefinition> found = new LinkedHashSet<>();
        for(WorkerResourceKey output : outputs) found.addAll(dependencies(output));
        return List.copyOf(found);
    }

    public synchronized List<WorkerRecipeDefinition> dependencies(WorkerResourceKey output){
        return dependencies.computeIfAbsent(output, key -> {
            Set<WorkerRecipeDefinition> found = new LinkedHashSet<>();
            Set<WorkerResourceKey> visited = new HashSet<>();
            ArrayDeque<WorkerResourceKey> pending = new ArrayDeque<>();
            pending.add(key);
            while(!pending.isEmpty()){
                WorkerResourceKey resource = pending.removeLast();
                if(!visited.add(resource)) continue;
                for(WorkerRecipeDefinition def : producing(resource)){
                    found.add(def);
                    for(WorkerRecipeDefinition.Ingredient ingredient : def.ingredients()){
                        pending.addAll(ingredient.alternatives());
                    }
                }
            }
            return List.copyOf(found);
        });
    }

    // Identify stocked resources that can affect a requested recipe chain
    public synchronized Set<WorkerResourceKey> relevantResources(WorkerResourceKey output){
        return relevantResources.computeIfAbsent(output, key -> {
            Set<WorkerResourceKey> relevant = new HashSet<>();
            relevant.add(key);
            for(WorkerRecipeDefinition def : dependencies(key)){
                for(WorkerRecipeDefinition.Ingredient ingredient : def.ingredients())
                    relevant.addAll(ingredient.alternatives());
            }
            return Set.copyOf(relevant);
        });
    }
}
