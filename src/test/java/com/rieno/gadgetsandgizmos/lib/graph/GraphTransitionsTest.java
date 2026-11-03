package com.rieno.gadgetsandgizmos.lib.graph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Verify condition edges and numeric steps independently of graph sampling
class GraphTransitionsTest{
    // A held condition fires only at its false-to-true transition
    @Test
    void heldConditionsRearmOnlyAfterBecomingFalse(){
        boolean prev = false;
        int pulses = 0;
        for(boolean val : new boolean[]{false, true, true, true, false, false, true}){
            if(GraphTransitions.risingEdge(prev, val)) pulses++;
            prev = val;
        }
        assertEquals(2, pulses);
    }

    // Numeric steps work in either direction and stop exactly at the target
    @Test
    void stepsRemainBoundedWhenTheDirectionChanges(){
        double val = GraphTransitions.approach(0, 1, .25);
        assertEquals(.25, val);
        val = GraphTransitions.approach(val, 0, .1);
        assertEquals(.15, val, 1e-12);
        assertEquals(0, GraphTransitions.approach(val, 0, .5));
        assertEquals(-1, GraphTransitions.approach(0, -1, -2));
        assertEquals(1, GraphTransitions.approach(0, 1, 2));
        assertEquals(.5, GraphTransitions.approach(.5, 1, 0));
    }

    // Invalid rates cannot inject non-finite values into retained state
    @Test
    void invalidValuesKeepTransitionsFinite(){
        assertEquals(.5, GraphTransitions.approach(.5, 1, Double.NaN));
        assertEquals(.5, GraphTransitions.approach(.5, Double.POSITIVE_INFINITY, .1));
        assertEquals(.1, GraphTransitions.approach(Double.NaN, 1, .1));
        assertEquals(0, GraphTransitions.interpolate(Double.NaN, 1, Double.NaN));
    }

    // Repeated progress steps produce intermediate values and clamp at the endpoint
    @Test
    void interpolationAdvancesByBoundedProgress(){
        double progress = 0;
        for(int idx = 1; idx <= 4; idx++){
            progress = GraphTransitions.approach(progress, 1, .25);
            assertEquals(idx * 2.5, GraphTransitions.interpolate(0, 10, progress));
        }
        assertEquals(10, GraphTransitions.interpolate(0, 10, 2));
        assertEquals(0, GraphTransitions.interpolate(0, 10, -1));
        assertEquals(0, GraphTransitions.interpolate(-Double.MAX_VALUE, Double.MAX_VALUE, .5));
    }
}
