package com.rieno.gadgetsandgizmos.lib.graph;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GraphCanvasSearchTest{
    @Test void matchesTitlesAliasesAndTypesAndWrapsInBothDirections(){
        var first = new Node("one", "flip_flop", "Landing gear");
        var second = new Node("two", "flip_flop", "Door");
        var nodes = List.of(first, second);
        assertEquals(List.of(first), GraphCanvasSearch.matches(nodes, " LANDING ", Node::label));
        var matches = GraphCanvasSearch.matches(nodes, "FLIP_FLOP", Node::label);
        assertEquals(first, GraphCanvasSearch.next(matches, null, false));
        assertEquals(second, GraphCanvasSearch.next(matches, null, true));
        assertEquals(first, GraphCanvasSearch.next(matches, second.id(), false));
        assertEquals(second, GraphCanvasSearch.next(matches, first.id(), true));
        assertNull(GraphCanvasSearch.next(GraphCanvasSearch.matches(nodes, "", Node::label), null, false));
    }
    @Test void centeringUsesScreenPixelsAtEveryZoom(){
        for(double zoom : new double[]{.5, 1, 2}){
            var viewport = new com.rieno.gadgetsandgizmos.lib.graph.render.GraphViewport(180, 48, 20, -30, zoom)
                    .centerOn(-300, 250, 800, 600);
            assertEquals(580, viewport.screenX(-300));
            assertEquals(348, viewport.screenY(250));
        }
    }
    private record Node(String id, String type, String label) implements GraphModel.Node{}
}
