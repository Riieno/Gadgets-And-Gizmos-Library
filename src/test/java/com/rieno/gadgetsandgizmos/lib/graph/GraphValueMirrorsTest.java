package com.rieno.gadgetsandgizmos.lib.graph;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GraphValueMirrorsTest{
    @Test void authoritativeSamplesPreserveZeroFalseAndStructuredValues(){
        Map<String, Object> live = Map.of("number", 0, "boolean", false, "map", Map.of("value", 42));
        for(String key : live.keySet()){
            assertSame(live.get(key), GraphValueMirrors.sample(live, key, ignored -> true,
                    () -> { throw new AssertionError("An authoritative sample must not evaluate its fallback"); }));
        }
    }

    @Test void missingAndNonAuthoritativePortsEvaluateTheirCurrentWiring(){
        Map<String, Integer> live = Map.of("source", 7);
        assertEquals(8, GraphValueMirrors.sample(live, "source", ignored -> false, () -> 8));
        assertEquals(9, GraphValueMirrors.sample(live, "missing", ignored -> true, () -> 9));
        assertEquals(Map.of("source", 7), live);
    }
}
