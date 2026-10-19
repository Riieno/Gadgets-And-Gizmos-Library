package com.rieno.gadgetsandgizmos.lib.graph;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GraphBoundedLoopTest{
    @Test void activeRunsIgnoreNewStartsAndPublishEveryIndexBeforeCompleting(){
        var run = GraphBoundedLoop.start(GraphBoundedLoop.State.idle(), 4, 256);
        List<Integer> indices = new ArrayList<>();
        for(int idx = 0; idx < 4; idx++){
            assertSame(run, GraphBoundedLoop.start(run, 1, 256));
            var step = GraphBoundedLoop.advance(run);
            assertTrue(step.iteration());
            indices.add(step.index());
            assertEquals(idx == 3, step.complete());
            run = step.state();
        }
        assertEquals(List.of(0, 1, 2, 3), indices);
        assertFalse(run.running());
        assertFalse(GraphBoundedLoop.advance(run).complete());
        assertTrue(GraphBoundedLoop.start(run, 2, 256).running());
    }

    @Test void emptyInvalidAndExcessiveRunsRemainBounded(){
        for(double requested : new double[]{0, -5, Double.NaN, Double.POSITIVE_INFINITY}){
            var step = GraphBoundedLoop.advance(GraphBoundedLoop.start(GraphBoundedLoop.State.idle(), requested, 256));
            assertFalse(step.iteration());
            assertTrue(step.complete());
            assertFalse(step.state().running());
        }
        assertEquals(256, GraphBoundedLoop.start(GraphBoundedLoop.State.idle(), 10000, 256).iterations());
        assertEquals(3, GraphBoundedLoop.start(GraphBoundedLoop.State.idle(), 3.9, 256).iterations());
    }
}
