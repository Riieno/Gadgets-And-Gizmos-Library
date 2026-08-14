package com.rieno.gadgetsandgizmos.lib.graph;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.Map;

// Describe one node type exactly as it is exposed by a graph host
public record GraphNodeDefinition(
        String id,
        String category,
        Map<String, String> inputs,
        Map<String, String> outputs,
        boolean stateful
) {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the graph node definition
    public GraphNodeDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("A graph node definition requires an id");
        }
        category = category == null || category.isBlank() ? "core" : category;
        inputs = Map.copyOf(inputs == null ? Map.of() : inputs);
        outputs = Map.copyOf(outputs == null ? Map.of() : outputs);
    }
}
