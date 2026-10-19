package com.rieno.gadgetsandgizmos.lib.graph;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GraphPlayerScopePlanTest{
    @Test void attachingPersonalActionsCannotReclassifyTheirProducers(){
        for(String display : List.of("world_widget", "hud")){
            var graph = new Graph(List.of(new TestNode("key", "key"), new TestNode("flip", "flip"),
                    new TestNode("display", display), new TestNode("counter", "counter"),
                    new TestNode("camera", "camera")), List.of(
                    new TestEdge("pulse", "key", "exec", "flip", "exec"),
                    new TestEdge("visible", "flip", "value", "display", "visible"),
                    new TestEdge("click", "display", "exec", "counter", "exec"),
                    new TestEdge("view", "counter", "exec", "camera", "exec")));
            var without = GraphPlayerScopePlan.analyze(graph, node -> node.type().equals("hud"), node -> false,
                    node -> "", node -> "", edge -> edge.fromPort().equals("exec"), node -> node.type().equals("key"));
            var with = GraphPlayerScopePlan.analyze(graph, node -> node.type().equals("hud"), node -> node.type().equals("camera"),
                    node -> "", node -> "", edge -> edge.fromPort().equals("exec"), node -> node.type().equals("key"));
            assertEquals(without.nodes().contains("flip"), with.nodes().contains("flip"));
            assertEquals(display.equals("hud"), with.nodes().contains("flip"));
            assertFalse(with.nodes().contains("counter"));
        }
    }

    @Test void observingASharedFlipFlopDoesNotMakeItsStatePersonal(){
        var graph = graph("tick");
        var plan = analyze(graph);
        assertTrue(plan.nodes().contains("hud"));
        assertFalse(plan.nodes().contains("flip"));
        assertFalse(plan.nodes().contains("expression"));
        assertFalse(plan.variables().contains("enabled"));
    }

    @Test void personalInputKeepsItsOwnFlipFlopAndVariableStateThroughExpressions(){
        var plan = analyze(graph("key"));
        assertTrue(plan.nodes().containsAll(List.of("flip", "expression", "writer", "reader", "hud")));
        assertTrue(plan.variables().contains("enabled"));
        assertTrue(plan.containsStateKey("flip:value"));
        assertTrue(plan.containsStateKey("variable:enabled"));
    }

    private static GraphPlayerScopePlan analyze(Graph graph){
        return GraphPlayerScopePlan.analyze(graph, node -> node.type().equals("hud"), node -> false,
                node -> node.type().equals("get") ? "enabled" : "",
                node -> node.type().equals("set") ? "enabled" : "",
                edge -> edge.fromPort().equals("exec"), node -> node.type().equals("key"));
    }

    private static Graph graph(String source){
        return new Graph(List.of(new TestNode("source", source), new TestNode("flip", "flip"),
                new TestNode("expression", "math"), new TestNode("writer", "set"),
                new TestNode("reader", "get"), new TestNode("hud", "hud")), List.of(
                new TestEdge("pulse", "source", "exec", "flip", "exec"),
                new TestEdge("data", "flip", "value", "expression", "value"),
                new TestEdge("view", "expression", "value", "hud", "visible"),
                new TestEdge("set", "flip", "exec", "writer", "exec"),
                new TestEdge("value", "flip", "value", "writer", "value"),
                new TestEdge("read", "reader", "value", "hud", "text")));
    }

    private record TestNode(String id, String type) implements GraphModel.Node{}
    private record TestEdge(String id, String fromNode, String fromPort, String toNode, String toPort) implements GraphModel.Edge{}
    private record Graph(List<TestNode> nodes, List<TestEdge> edges) implements GraphModel<TestNode, TestEdge>{}
}
