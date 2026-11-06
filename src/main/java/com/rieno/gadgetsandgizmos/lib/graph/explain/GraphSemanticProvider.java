package com.rieno.gadgetsandgizmos.lib.graph.explain;

import com.rieno.gadgetsandgizmos.lib.graph.GraphModel;

// Describe saved configuration without reading runtime values or changing the graph
@FunctionalInterface
public interface GraphSemanticProvider<N extends GraphModel.Node>{
    GraphNodeMeaning describe(N node);
}
