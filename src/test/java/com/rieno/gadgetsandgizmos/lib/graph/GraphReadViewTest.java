package com.rieno.gadgetsandgizmos.lib.graph;

import com.rieno.gadgetsandgizmos.lib.util.RevisionSnapshotCache;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GraphReadViewTest{
    @Test void retainsOrderedBorrowedNodesAndEdgesInUnmodifiableCollections(){
        Model graph = new Model();
        var first = new TestNode("first", "hud");
        var second = new TestNode("second", "widget");
        var edge = new TestEdge("edge", "first", "value", "second", "value");
        graph.nodes.add(first); graph.nodes.add(second); graph.edges.add(edge);
        var view = GraphReadView.of(graph);
        assertSame(first, view.nodes().getFirst());
        assertSame(second, view.node("second"));
        assertSame(edge, view.edges().getFirst());
        assertNull(view.node("missing"));
        assertThrows(UnsupportedOperationException.class, () -> view.nodes().clear());
        assertThrows(UnsupportedOperationException.class, () -> view.edges().add(edge));
        graph.nodes.clear(); graph.edges.clear();
        assertEquals(List.of(first, second), view.nodes());
        assertSame(second, view.node("second"));
        assertEquals(List.of(edge), view.edges());
    }

    @Test void cacheRefreshesForRevisionsAndSourceReplacementWithoutCloningPayloads(){
        Model graph = new Model();
        graph.nodes.add(new TestNode("old", "hud"));
        var cache = new RevisionSnapshotCache<GraphReadView<TestNode, TestEdge>>();
        var first = cache.get(graph, 7, () -> GraphReadView.of(graph));
        for(int i = 0; i < 1000; i++){
            assertSame(first, cache.get(graph, 7, () -> { throw new AssertionError("Layout rebuilt during a value update"); }));
        }
        graph.nodes.add(new TestNode("new", "widget"));
        var changed = cache.get(graph, 8, () -> GraphReadView.of(graph));
        assertNotSame(first, changed);
        assertSame(graph.nodes.getLast(), changed.node("new"));
        Model replacement = new Model();
        replacement.nodes.add(new TestNode("replacement", "hud"));
        var replaced = cache.get(replacement, 8, () -> GraphReadView.of(replacement));
        assertNotSame(changed, replaced);
        assertNull(replaced.node("old"));
        assertSame(replacement.nodes.getFirst(), replaced.node("replacement"));
    }

    private record TestNode(String id, String type) implements GraphModel.Node{}
    private record TestEdge(String id, String fromNode, String fromPort, String toNode, String toPort) implements GraphModel.Edge{}
    private static final class Model implements GraphModel<TestNode, TestEdge>{
        private final List<TestNode> nodes = new ArrayList<>();
        private final List<TestEdge> edges = new ArrayList<>();
        @Override public List<TestNode> nodes(){ return nodes; }
        @Override public List<TestEdge> edges(){ return edges; }
    }
}
