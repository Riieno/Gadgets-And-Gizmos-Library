package com.rieno.gadgetsandgizmos.lib.graph;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

// Connect registered node ids to their reusable runtime implementations
public final class GraphRuntimeRegistry {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracked executors
    private final Map<String, GraphNodeExecutor> executors = new LinkedHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Register the graph runtime
    public synchronized void register(String nodeType, GraphNodeExecutor executor) {
        if (nodeType == null || nodeType.isBlank()) {
            throw new IllegalArgumentException("A runtime node type is required");
        }
        Objects.requireNonNull(executor, "executor");
        GraphNodeExecutor prev = executors.putIfAbsent(nodeType, executor);
        if (prev != null && prev != executor) {
            throw new IllegalStateException("A runtime is already registered for " + nodeType);
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the graph runtime value
    public synchronized GraphNodeExecutor get(String nodeType) {
        return executors.get(nodeType);
    }

    // Get the snapshot
    public synchronized Map<String, GraphNodeExecutor> snapshot() {
        return Map.copyOf(executors);
    }
}
