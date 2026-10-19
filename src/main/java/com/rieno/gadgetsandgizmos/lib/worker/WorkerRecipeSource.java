package com.rieno.gadgetsandgizmos.lib.worker;

import java.util.List;
import java.util.Collection;

// Resolve detached producers only when a search needs an unstocked resource
@FunctionalInterface
public interface WorkerRecipeSource{
    List<WorkerRecipeDefinition> producing(WorkerResourceKey resource);

    // Share ingredient relationships without expanding combinations of recipe trees
    default WorkerRecipeRelationships relationships(Collection<WorkerResourceKey> outputs){
        return WorkerRecipeRelationships.discover(this, outputs);
    }
}
