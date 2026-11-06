package com.rieno.gadgetsandgizmos.lib.graph.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Verify that viewport changes cannot reflow or reposition an authored callout
class GraphCalloutLayoutTest{
    // Preserve the node-relative offset and scale every dimension at each editor zoom
    @Test
    void panAndZoomPreserveTheAuthoredAttachment(){
        double nodeX = -37.25, nodeY = 283.75, nodeWidth = 166;
        var bubble = GraphCalloutLayout.above(nodeX, nodeY, nodeWidth, 250, 78);
        for(double zoom : new double[]{0.1, 0.35, 0.8, 1, 2, 4}){
            for(double pan : new double[]{-1024.5, 0, 913.25}){
                var viewport = new GraphViewport(173, 104, pan, -pan, zoom);
                assertEquals((nodeWidth - bubble.width()) / 2,
                        (viewport.screenX(bubble.x()) - viewport.screenX(nodeX)) / zoom, 1.0e-8);
                assertEquals(10, (viewport.screenY(nodeY) - viewport.screenY(bubble.y() + bubble.height())) / zoom, 1.0e-8);
                assertEquals(250 * zoom, viewport.screenX(bubble.x() + bubble.width()) - viewport.screenX(bubble.x()), 1.0e-8);
                assertEquals(78 * zoom, viewport.screenY(bubble.y() + bubble.height()) - viewport.screenY(bubble.y()), 1.0e-8);
            }
        }
    }

    // Keep pointer hit testing attached to the transformed bubble rather than its old screen position
    @Test
    void pointerHitsUseTheCurrentViewport(){
        var bubble = GraphCalloutLayout.above(80, 250, 180, 250, 78);
        var first = new GraphViewport(0, 0, 0, 0, 1);
        var next = new GraphViewport(173, 104, -60, 30, 0.35);
        assertTrue(bubble.contains(first, first.screenX(170), first.screenY(235)));
        assertTrue(bubble.contains(next, next.screenX(170), next.screenY(235)));
        assertFalse(bubble.contains(next, first.screenX(170), first.screenY(235)));
        assertFalse(bubble.contains(next, next.screenX(bubble.x() - 1), next.screenY(200)));
        assertFalse(bubble.contains(next, next.screenX(bubble.x() + bubble.width() + 1), next.screenY(200)));
    }

    // Clip a partially visible callout without shifting it away from its node
    @Test
    void cullingDoesNotClampTheLayoutToTheViewport(){
        var bubble = GraphCalloutLayout.above(80, 250, 180, 250, 78);
        var clipped = new GraphViewport(0, 0, -80, -80, 1);
        assertEquals(-35, clipped.screenX(bubble.x()), 1.0e-9);
        assertTrue(bubble.intersects(clipped, 0, 0, 700, 520));
        assertFalse(bubble.intersects(clipped.panBy(-1000, 0), 0, 0, 700, 520));
        assertEquals(45, bubble.x(), 1.0e-9);
        assertEquals(162, bubble.y(), 1.0e-9);
    }
}
