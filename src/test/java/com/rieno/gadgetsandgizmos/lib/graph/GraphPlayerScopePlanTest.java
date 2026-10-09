package com.rieno.gadgetsandgizmos.lib.graph;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class GraphPlayerScopePlanTest{
    @Test void followsIndirectVariablesAndTheirStatefulExecutionPaths(){
        var graph = new Model(List.of(new Node("hud","ui",""),new Node("read-a","read","a"),
                new Node("write-a","write","a"),new Node("read-b","read","b"),
                new Node("write-b","write","b"),new Node("toggle","logic",""),
                new Node("button","input",""),new Node("motor","mechanical","")),
                List.of(edge("read-a","value","hud"),edge("read-b","value","write-a"),
                        edge("toggle","value","write-b"),edge("button","exec","toggle")));
        var plan = GraphPlayerScopePlan.analyze(graph,node -> "ui".equals(node.type()), node -> false,
                node -> "read".equals(node.type()) ? node.variable : "",
                node -> "write".equals(node.type()) ? node.variable : "", edge -> "exec".equals(edge.fromPort()));
        assertEquals(Set.of("a","b"),plan.variables());
        assertTrue(plan.containsStateKey("toggle:flip_flop"));
        assertTrue(plan.containsStateKey("variable:b"));
        assertFalse(plan.containsStateKey("motor:position"));
        assertFalse(plan.containsStateKey("variable:shared"));
    }
    private static Edge edge(String from,String port,String to){ return new Edge(from+to,from,port,to,"input"); }
    private record Node(String id,String type,String variable) implements GraphModel.Node{}
    private record Edge(String id,String fromNode,String fromPort,String toNode,String toPort) implements GraphModel.Edge{}
    private record Model(List<GraphPlayerScopePlanTest.Node> nodes,List<GraphPlayerScopePlanTest.Edge> edges)
            implements GraphModel<GraphPlayerScopePlanTest.Node,GraphPlayerScopePlanTest.Edge>{}
}
