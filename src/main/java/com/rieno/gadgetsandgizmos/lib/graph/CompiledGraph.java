package com.rieno.gadgetsandgizmos.lib.graph;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.List;
import java.util.Map;

// Store the compiled node and wire lookups so the graph does not rebuild them while running
public final class CompiledGraph<N extends GraphModel.Node, E extends GraphModel.Edge> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracked nodes
    private final Map<String, N> nodes;
    // Tracked incoming
    private final Map<String, List<E>> incoming;
    // Tracked outgoing
    private final Map<Port, List<E>> outgoing;
    // Tracked diagnostics
    private final List<String> diagnostics;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the compiled graph
    CompiledGraph(Map<String, N> nodes, Map<String, List<E>> incoming,
                  Map<Port, List<E>> outgoing, List<String> diagnostics) {
        this.nodes = Map.copyOf(nodes);
        this.incoming = GraphCompiler.freezeListMap(incoming);
        this.outgoing = GraphCompiler.freezeListMap(outgoing);
        this.diagnostics = List.copyOf(diagnostics);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the node
    public N node(String id) {
        return nodes.get(id);
    }

    // Get the nodes
    public Map<String, N> nodes() {
        return nodes;
    }

    // Get the incoming
    public List<E> incoming(String nodeId) {
        return incoming.getOrDefault(nodeId, List.of());
    }

    // Get the outgoing
    public List<E> outgoing(String nodeId, String port) {
        return outgoing.getOrDefault(new Port(nodeId, port), List.of());
    }

    // Get the diagnostics
    public List<String> diagnostics() {
        return diagnostics;
    }

    // Store the port
    public record Port(String nodeId, String port) {
    }
}
