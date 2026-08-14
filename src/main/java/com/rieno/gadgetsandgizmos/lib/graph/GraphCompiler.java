package com.rieno.gadgetsandgizmos.lib.graph;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Build the reusable graph lookups without pulling Minecraft or rendering code into the library
public final class GraphCompiler {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the graph compiler
    private GraphCompiler() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Compile the graph compiler
    public static <N extends GraphModel.Node, E extends GraphModel.Edge>
    CompiledGraph<N, E> compile(GraphModel<N, E> graph) {
        Map<String, N> nodes = new LinkedHashMap<>();
        Map<String, List<E>> incoming = new HashMap<>();
        Map<CompiledGraph.Port, List<E>> outgoing = new HashMap<>();
        List<String> diagnostics = new ArrayList<>();
        if (graph == null) {
            return new CompiledGraph<>(nodes, incoming, outgoing, diagnostics);
        }
        for (N node : graph.nodes()) {
            if (node == null || node.id() == null || node.id().isBlank()) {
                diagnostics.add("Ignored a graph node without an id");
                continue;
            }
            if (nodes.putIfAbsent(node.id(), node) != null) {
                diagnostics.add("Duplicate graph node id: " + node.id());
            }
        }
        for (E edge : graph.edges()) {
            if (edge == null || !nodes.containsKey(edge.fromNode()) || !nodes.containsKey(edge.toNode())) {
                diagnostics.add("Ignored an edge whose endpoint is missing: "
                        + (edge == null ? "<null>" : edge.id()));
                continue;
            }
            incoming.computeIfAbsent(edge.toNode(), ignored -> new ArrayList<>()).add(edge);
            outgoing.computeIfAbsent(new CompiledGraph.Port(edge.fromNode(), edge.fromPort()),
                    ignored -> new ArrayList<>()).add(edge);
        }
        return new CompiledGraph<>(nodes, incoming, outgoing, diagnostics);
    }

    // Get the freeze list map
    static <K, V> Map<K, List<V>> freezeListMap(Map<K, List<V>> src) {
        Map<K, List<V>> frozen = new HashMap<>();
        src.forEach((key, val) -> frozen.put(key, List.copyOf(val)));
        return Map.copyOf(frozen);
    }
}
