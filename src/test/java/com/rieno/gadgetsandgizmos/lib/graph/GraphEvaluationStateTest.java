package com.rieno.gadgetsandgizmos.lib.graph;

import org.junit.jupiter.api.Test;
<<<<<<< Updated upstream
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
=======
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class GraphEvaluationStateTest{
    @Test
    void evaluationDetachesOnlyReadStateAndPreservesHostOnFailure(){
        var runtime = new HashMap<String, StringBuilder>();
        runtime.put("counter", new StringBuilder("1"));
        runtime.put("untouched", new StringBuilder("2"));
        var state = new GraphEvaluationState<>(runtime);
        var copies = new AtomicInteger();
        var fork = state.fork(val -> { copies.incrementAndGet(); return new StringBuilder(val); });
        assertEquals(0, copies.get());
        assertThrows(IllegalStateException.class, () -> state.evaluate(fork, () -> {
            state.get("counter").append("0");
            state.put("new", new StringBuilder("3"));
            throw new IllegalStateException();
        }));
        assertEquals(1, copies.get());
        assertEquals("1", state.get("counter").toString());
        assertFalse(state.containsKey("new"));
        assertEquals("10", fork.get("counter").toString());
        fork.keySet().removeIf(key -> key.equals("counter"));
        assertFalse(fork.containsKey("counter"));
        assertTrue(runtime.containsKey("counter"));
        fork.clear();
        assertTrue(fork.isEmpty());
        assertEquals(2, runtime.size());
>>>>>>> Stashed changes
    }
}
