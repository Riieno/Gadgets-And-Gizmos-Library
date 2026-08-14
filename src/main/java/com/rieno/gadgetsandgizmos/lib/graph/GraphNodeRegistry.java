package com.rieno.gadgetsandgizmos.lib.graph;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

// Keep published node types ordered and reject conflicting duplicate registrations
public final class GraphNodeRegistry {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracked definitions
    private final Map<String, GraphNodeDefinition> definitions = new LinkedHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Register the graph node
    public synchronized GraphNodeDefinition register(GraphNodeDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        GraphNodeDefinition prev = definitions.putIfAbsent(definition.id(), definition);
        if (prev != null && !prev.equals(definition)) {
            throw new IllegalStateException("Graph node type is already registered: " + definition.id());
        }
        return prev == null ? definition : prev;
    }

    // Register the graph node
    public synchronized GraphNodeDefinition register(
            String id,
            String category,
            Map<String, String> inputs,
            Map<String, String> outputs,
            boolean stateful
    ) {
        return register(new GraphNodeDefinition(id, category, inputs, outputs, stateful));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the graph node value
    public synchronized GraphNodeDefinition get(String id) {
        return definitions.get(id);
    }

    // Check if this contains the value
    public synchronized boolean contains(String id) {
        return definitions.containsKey(id);
    }

    // Get the definitions
    public synchronized Collection<GraphNodeDefinition> definitions() {
        return definitions.values().stream().toList();
    }

    // Get the snapshot
    public synchronized Map<String, GraphNodeDefinition> snapshot() {
        return Map.copyOf(definitions);
    }
}
