package com.rieno.gadgetsandgizmos.lib.graph;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GraphPreviewPlanTest{
    @Test void layoutChangesKeepSnapshotsWhileSemanticChangesInvalidateEveryConsumer(){
        var source = graph(1, 0);
        var moved = graph(1, 100);
        var plan = GraphPreviewPlan.compile(source, moved, (prev, node) -> prev.setting == node.setting);
        for(String id : List.of("source", "math", "display", "unrelated")) assertTrue(plan.unchanged(id));
        plan = GraphPreviewPlan.compile(source, graph(2, 100), (prev, node) -> prev.setting == node.setting);
        for(String id : List.of("source", "math", "display")) assertFalse(plan.unchanged(id));
        assertTrue(plan.unchanged("unrelated"));
    }

    @Test void rewiringAndRemovedSourcesInvalidateCyclesWithoutLosingUnrelatedNodes(){
        var source = graph(1, 0);
        var rewired = new Model(source.nodes, List.of(
                new PreviewEdge("unrelated", "value", "math", "a"),
                new PreviewEdge("math", "value", "display", "a"),
                new PreviewEdge("display", "value", "math", "b")));
        var plan = GraphPreviewPlan.compile(source, rewired, (prev, node) -> prev.setting == node.setting);
        assertFalse(plan.unchanged("math"));
        assertFalse(plan.unchanged("display"));
        assertTrue(plan.unchanged("source"));
        var removed = new Model(source.nodes.stream().filter(node -> !node.id.equals("source")).toList(), source.edges);
        plan = GraphPreviewPlan.compile(source, removed, (prev, node) -> prev.setting == node.setting);
        assertFalse(plan.unchanged("math"));
        assertFalse(plan.unchanged("display"));
        assertTrue(plan.unchanged("unrelated"));
    }

    private static Model graph(int setting, int x){
        return new Model(List.of(new PreviewNode("source", setting, x), new PreviewNode("math", 1, x),
                new PreviewNode("display", 1, x), new PreviewNode("unrelated", 1, x)), List.of(
                new PreviewEdge("source", "value", "math", "a"), new PreviewEdge("math", "value", "display", "a")));
    }
    private record PreviewNode(String id, int setting, int x) implements GraphModel.Node{
        public String type(){ return "test"; }
    }
    private record PreviewEdge(String fromNode, String fromPort, String toNode, String toPort) implements GraphModel.Edge{
        public String id(){ return fromNode + toNode; }
    }
    private record Model(List<PreviewNode> nodes, List<PreviewEdge> edges) implements GraphModel<PreviewNode, PreviewEdge>{}
}
