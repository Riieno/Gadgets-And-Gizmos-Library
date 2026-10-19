package com.rieno.gadgetsandgizmos.lib.graph;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GraphFlipPulseTest{
    @Test void modesSkipTheRequestedPulsesAndRepeatTheirCadence(){
        for(var mode : GraphCounters.FlipMode.values()){
            int skip = switch(mode){ case EVERY_PULSE -> 0; case EVERY_SECOND_PULSE -> 1; case EVERY_NTH_PULSE -> 3; };
            boolean value = false;
            int skipped = 0;
            for(int pulse = 1; pulse <= (skip + 1) * 4; pulse++){
                var result = GraphCounters.flipPulse(value, skipped, mode, 3);
                assertEquals(pulse % (skip + 1) == 0, result.flipped());
                assertEquals((pulse / (skip + 1)) % 2 == 1, result.value());
                value = result.value();
                skipped = result.skipped();
            }
        }
    }

    @Test void zeroAndInvalidSkipCountsFlipOnEveryPulseWithoutOverflow(){
        for(double skip : new double[]{0, -1, Double.NaN}){
            assertTrue(GraphCounters.flipPulse(false, 0, GraphCounters.FlipMode.EVERY_NTH_PULSE, skip).flipped());
        }
        assertTrue(GraphCounters.flipPulse(false, Integer.MAX_VALUE, GraphCounters.FlipMode.EVERY_NTH_PULSE, Integer.MAX_VALUE).flipped());
        assertEquals(GraphCounters.FlipMode.EVERY_PULSE, GraphCounters.FlipMode.fromId(""));
        assertEquals(GraphCounters.FlipMode.EVERY_SECOND_PULSE, GraphCounters.FlipMode.fromId("Every Second Pulse"));
        assertEquals("number", GraphComparison.operandType("float", "string"));
        assertEquals("string", GraphComparison.operandType("any", "string"));
        assertEquals("any", GraphComparison.operandType(null, null));
    }
}
