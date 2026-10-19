package com.rieno.gadgetsandgizmos.lib.graph;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class GraphEvaluationCacheTest{
    @Test void switchingPlayersOrReturningToSharedExecutionDiscardsEvaluatedPorts(){
        var context = new AtomicReference<UUID>();
        var cache = new GraphEvaluationCache<String, Boolean>(context::get);
        cache.put("visible", false);
        assertEquals(false, cache.get("visible"));
        UUID first = UUID.randomUUID();
        context.set(first);
        assertFalse(cache.containsKey("visible"));
        cache.put("visible", true);
        context.set(UUID.fromString(first.toString()));
        assertEquals(true, cache.get("visible"));
        context.set(UUID.randomUUID());
        assertTrue(cache.isEmpty());
        cache.put("visible", false);
        context.set(null);
        assertNull(cache.get("visible"));
        cache.put("visible", true);
        cache.clear();
        assertTrue(cache.isEmpty());
    }

    @Test void invalidatingDependentPortsDoesNotRemoveUnrelatedEvaluations(){
        var cache = new GraphEvaluationCache<String, Boolean>(() -> null);
        cache.put("changed:value", false);
        cache.put("unrelated:value", true);
        cache.keySet().removeIf(key -> key.startsWith("changed:"));
        assertFalse(cache.containsKey("changed:value"));
        assertEquals(true, cache.get("unrelated:value"));
    }
}
