package com.rieno.gadgetsandgizmos.lib.graph;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class GraphEvaluationStateTest{
    @Test void nestedSamplesAndFailuresLeaveRuntimeStateUntouched(){
        Map<String, Integer> runtime = new LinkedHashMap<>(Map.of("count", 7));
        var state = new GraphEvaluationState<>(runtime);
        Map<String, Integer> first = state.snapshot(value -> value);
        assertThrows(IllegalStateException.class, () -> state.evaluate(first, () -> {
            state.put("count", 8);
            state.evaluate(new LinkedHashMap<>(Map.of("count", 99)), () -> {
                assertEquals(99, state.get("count"));
                state.remove("count");
                return null;
            });
            assertEquals(8, state.get("count"));
            assertTrue(state.evaluating());
            throw new IllegalStateException("Failed preview");
        }));
        assertFalse(state.evaluating());
        assertEquals(7, state.get("count"));
        assertEquals(8, first.get("count"));
        state.put("count", 9);
        assertEquals(9, runtime.get("count"));
    }

    @Test void hostCopierProtectsMutableStateAndSamplesKeepTheirOwnHistory(){
        var state = new GraphEvaluationState<List<Integer>>(new LinkedHashMap<>(Map.of("values", new ArrayList<>(List.of(1)))));
        var preview = state.snapshot(ArrayList::new);
        state.evaluate(preview, () -> { state.get("values").add(2); return null; });
        state.evaluate(preview, () -> { assertEquals(List.of(1, 2), state.get("values")); return null; });
        assertEquals(List.of(1), state.get("values"));
    }
}
