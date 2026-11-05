package com.rieno.gadgetsandgizmos.lib.control;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// Verify that timing, endpoint changes and independent fields all contribute to sequence recognition
class AlternatingValueSequenceTest{
    private static AlternatingValueSequence sequence(){
        return new AlternatingValueSequence(Set.of("north", "south"), -360, 360, 32, 4);
    }

    // Separate face writes and redundant assignments must not prevent a valid pair of full cycles
    @Test
    void acceptsIndependentFieldsAfterFourReversals(){
        var sequence = sequence();
        for(int idx = 0; idx <= 4; idx++){
            double val = idx % 2 == 0 ? -360 : 360;
            assertFalse(sequence.observe(idx * 32, Map.of("north", val)));
            assertEquals(idx == 4, sequence.observe(idx * 32, Map.of("south", -val)));
            assertEquals(idx == 4, sequence.observe(idx * 32 + 1, Map.of("north", val, "south", -val)));
        }
        assertFalse(sequence.complete(161));
    }

    // Wrong endpoints, missing fields and one-shot changes cannot complete the sequence
    @Test
    void rejectsPartialAndInterruptedSequences(){
        var sequence = sequence();
        for(int idx = 0; idx <= 6; idx++){
            assertFalse(sequence.observe(idx * 32, Map.of("north", idx % 2 == 0 ? -360.0 : 360.0)));
        }
        sequence.clear();
        for(int idx = 0; idx <= 4; idx++){
            double val = idx % 2 == 0 ? -360 : 360;
            assertFalse(sequence.observe(idx * 32, Map.of("north", val, "south", val / 2)));
        }
        assertFalse(sequence.observe(160, Map.of("north", Double.NaN, "south", 360.0)));
    }

    // Faster pulses, pauses and time rewinds must start a new observation window
    @Test
    void rejectsWrongTimingAndStaleProgress(){
        for(long period : new long[]{0, 16, 31, 33, 64}){
            var sequence = sequence();
            for(int idx = 0; idx <= 8; idx++){
                double val = idx % 2 == 0 ? -360 : 360;
                assertFalse(sequence.observe(idx * period, Map.of("north", val, "south", val)));
            }
        }
        var sequence = sequence();
        for(int idx = 0; idx <= 4; idx++){
            double val = idx % 2 == 0 ? -360 : 360;
            sequence.observe(idx * 32, Map.of("north", val, "south", val));
        }
        assertTrue(sequence.complete(128));
        assertFalse(sequence.observe(0, Map.of("north", -360.0, "south", -360.0)));
        sequence.clear();
        assertFalse(sequence.complete(128));
    }
}
