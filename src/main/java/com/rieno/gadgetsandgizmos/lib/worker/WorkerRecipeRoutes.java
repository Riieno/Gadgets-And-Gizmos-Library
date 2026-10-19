package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.List;
import java.util.Set;

// Prune unavailable processors before expanding their recipe prerequisites
public record WorkerRecipeRoutes(WorkerRecipeSource source, Set<ResourceLocation> processorTypes) implements WorkerRecipeSource{
    private static final java.util.Map<WorkerRecipeSource, java.util.Map<Key, WorkerRecipeRelationships>> CACHE = new java.util.WeakHashMap<>();
    private record Key(Set<ResourceLocation> types, List<WorkerResourceKey> outputs){}

    static void invalidate(){ synchronized(CACHE){ CACHE.clear(); } }
    public WorkerRecipeRoutes{
        if(source == null) throw new IllegalArgumentException("Recipe routes need a source");
        processorTypes = Set.copyOf(processorTypes);
    }

    @Override public List<WorkerRecipeDefinition> producing(WorkerResourceKey output){
        return source.producing(output).stream().filter(this::allows).toList();
    }

    public boolean allows(WorkerRecipeDefinition recipe){
        return recipe.operation() == WorkerRecipePlan.Operation.WORKER_CRAFTING
                || processorTypes.contains(recipe.processorType());
    }

    public List<WorkerRecipeDefinition> unavailable(WorkerResourceKey output){
        return source.producing(output).stream().filter(recipe -> !allows(recipe)).toList();
    }

    @Override public WorkerRecipeRelationships relationships(Collection<WorkerResourceKey> outputs){
        Key key = new Key(processorTypes, List.copyOf(outputs));
        synchronized(CACHE){
            var cached = CACHE.get(source);
            if(cached != null && cached.containsKey(key)) return cached.get(key);
        }
        var res = WorkerRecipeRelationships.discover(this, outputs);
        if(res.recipeCount() > 250_000) return res;
        synchronized(CACHE){
            var cached = CACHE.computeIfAbsent(source, val -> new java.util.LinkedHashMap<>(16, 0.75F, true));
            if(cached.containsKey(key)) return cached.get(key);
            while(!cached.isEmpty() && (cached.size() >= 32 || cached.values().stream()
                    .mapToInt(WorkerRecipeRelationships::recipeCount).sum() + res.recipeCount() > 250_000))
                cached.remove(cached.keySet().iterator().next());
            cached.put(key, res);
            return res;
        }
    }
}
