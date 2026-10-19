package com.rieno.gadgetsandgizmos.lib.graph;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An ordered, read-only topology for observers on the graph owner's thread.
 * Node and edge objects are borrowed, never cloned, and must be treated as read-only.
 * Retain this view until the source graph's identity or revision changes.
 */
public final class GraphReadView<N extends GraphModel.Node, E extends GraphModel.Edge>
        implements GraphModel<N, E>{
    private final List<N> nodes;
    private final List<E> edges;
    private final Map<String, N> byId;

    private GraphReadView(GraphModel<N, E> graph){
        nodes = List.copyOf(graph.nodes());
        edges = List.copyOf(graph.edges());
        Map<String, N> index = new LinkedHashMap<>();
        for(N node : nodes) index.putIfAbsent(node.id(), node);
        byId = Map.copyOf(index);
    }

    // Index existing topology without serializing graph data or duplicating node payloads
    public static <N extends GraphModel.Node, E extends GraphModel.Edge>
    GraphReadView<N, E> of(GraphModel<N, E> graph){
        return new GraphReadView<>(Objects.requireNonNull(graph, "graph"));
    }

    @Override public List<N> nodes(){ return nodes; }
    @Override public List<E> edges(){ return edges; }

    // Find a borrowed node without walking the graph for each rendered widget
    public N node(String id){ return byId.get(id); }
}
