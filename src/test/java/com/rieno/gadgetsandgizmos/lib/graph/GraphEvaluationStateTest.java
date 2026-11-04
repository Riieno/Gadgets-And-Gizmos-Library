package com.rieno.gadgetsandgizmos.lib.graph;

import org.junit.jupiter.api.Test;
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
    }
}
