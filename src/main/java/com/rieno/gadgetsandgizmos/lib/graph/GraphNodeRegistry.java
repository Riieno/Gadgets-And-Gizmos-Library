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
    private final Map<String, String> replacements = new LinkedHashMap<>();

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

    // Retain the runtime definition while preventing new instances of a deprecated type
    public synchronized void deprecate(String id, String replacement){
        if(!definitions.containsKey(id) || !definitions.containsKey(replacement)){
            throw new IllegalArgumentException("Deprecated nodes and their replacements must be registered");
        }
        String prev = replacements.putIfAbsent(id, replacement);
        if(prev != null && !prev.equals(replacement)) throw new IllegalStateException("Node replacement is already registered: " + id);
    }

    public synchronized boolean deprecated(String id){ return replacements.containsKey(id); }

    public synchronized String replacement(String id){ return replacements.getOrDefault(id, ""); }

    // Apply the marker to saved custom labels as well as default names
    public synchronized String label(String id, String label){
        return deprecated(id) && !label.endsWith(" [Deprecated]") ? label + " [Deprecated]" : label;
    }

    public synchronized Collection<GraphNodeDefinition> creatableDefinitions(){
        return definitions.values().stream().filter(definition -> !deprecated(definition.id())).toList();
    }

    // Get the snapshot
    public synchronized Map<String, GraphNodeDefinition> snapshot() {
        return Map.copyOf(definitions);
    }
}
