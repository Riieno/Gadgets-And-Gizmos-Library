package com.rieno.gadgetsandgizmos.lib.graph;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

// Remove retired graph nodes together with the wires they own
public final class GraphReconciliation{
    // Keep graph reconciliation stateless
    private GraphReconciliation(){}

    // Return removed nodes so the host can release their runtime bindings
    public static <N extends GraphModel.Node, E extends GraphModel.Edge> List<N> removeNodes(
            GraphModel<N, E> graph, Predicate<? super N> removed){
        Objects.requireNonNull(graph);
        Objects.requireNonNull(removed);
        List<N> nodes = graph.nodes().stream().filter(removed).toList();
        if(nodes.isEmpty()) return nodes;
        var ids = new HashSet<String>();
        nodes.forEach(node -> ids.add(node.id()));
        graph.edges().removeIf(edge -> ids.contains(edge.fromNode()) || ids.contains(edge.toNode()));
        graph.nodes().removeIf(node -> ids.contains(node.id()));
        return nodes;
    }
}
