package com.rieno.gadgetsandgizmos.lib.graph.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GraphViewportTest{
    @Test
    void zoomKeepsThePointerOnTheSameAuthoredPoint(){
        var prev = new GraphViewport(173, 104, -47, 31, 0.35);
        var next = prev.zoomAt(372, 221, 2.4);
        assertEquals(prev.graphX(372), next.graphX(372), 1.0e-9);
        assertEquals(prev.graphY(221), next.graphY(221), 1.0e-9);
    }

    @Test
    void panUsesScreenDistanceAtAnyZoom(){
        var prev = new GraphViewport(173, 104, 0, 0, 3.2);
        var next = prev.panBy(-60, 25);
        assertEquals(prev.screenX(12) - 60, next.screenX(12), 1.0e-9);
        assertEquals(prev.screenY(35) + 25, next.screenY(35), 1.0e-9);
    }

    @Test
    void configuredGridSnapsPositiveAndNegativeCoordinates(){
        assertEquals(32, GraphViewport.snapCoordinate(27, 16));
        assertEquals(-32, GraphViewport.snapCoordinate(-27, 16));
        assertEquals(24, GraphViewport.snapCoordinate(27, 8));
        assertThrows(IllegalArgumentException.class, () -> GraphViewport.snapCoordinate(12, 0));
    }

    @Test
    void visibleNudgesRemainConsistentAfterZoom(){
        var viewport = new GraphViewport(0, 0, 0, 0, 0.4);
        assertEquals(1, viewport.graphDistance(1) * viewport.zoom(), 1.0e-9);
        assertEquals(24, viewport.graphDistance(24) * viewport.zoom(), 1.0e-9);
    }
}
