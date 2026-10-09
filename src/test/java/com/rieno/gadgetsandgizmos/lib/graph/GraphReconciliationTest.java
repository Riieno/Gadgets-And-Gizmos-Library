package com.rieno.gadgetsandgizmos.lib.graph;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphReconciliationTest{
    // Retiring a source removes inbound and outbound wires while preserving unrelated nodes
    @Test
    void retiringOwnedNodesPreservesTheUserGraph(){
        var retained = new TestNode("retained", "manual");
        var retired = new TestNode("retired", "imported");
        var output = new TestNode("output", "manual");
        var retainedEdge = new TestEdge("retained-edge", "retained", "value", "output", "value");
        var graph = new Model(new ArrayList<>(List.of(retained, retired, output)), new ArrayList<>(List.of(
                new TestEdge("inbound", "retained", "value", "retired", "value"),
                new TestEdge("outbound", "retired", "value", "output", "value"), retainedEdge)));

        assertEquals(List.of(retired), GraphReconciliation.removeNodes(graph, node -> "imported".equals(node.type())));
        assertEquals(List.of(retained, output), graph.nodes());
        assertEquals(List.of(retainedEdge), graph.edges());
        assertTrue(GraphReconciliation.removeNodes(graph, node -> "imported".equals(node.type())).isEmpty());
    }

    private record TestNode(String id, String type) implements GraphModel.Node{}
    private record TestEdge(String id, String fromNode, String fromPort, String toNode, String toPort) implements GraphModel.Edge{}
    private record Model(List<TestNode> nodes, List<TestEdge> edges) implements GraphModel<TestNode, TestEdge>{}
}
