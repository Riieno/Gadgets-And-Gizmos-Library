package com.rieno.gadgetsandgizmos.lib.graph;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class GraphSaveStateTest{
    @Test void keepsLiveTypesAndValuesWithoutResurrectingDeletedVariables(){
        Map<String, GraphValue> live = Map.of("number", GraphValue.number(42), "text", GraphValue.string("flying"),
                "removed", GraphValue.bool(true));
        Map<String, GraphValue> saved = new LinkedHashMap<>(Map.of("number", GraphValue.number(0),
                "text", GraphValue.number(0), "new", GraphValue.number(7)));
        GraphSaveState.mergeVariables(live, saved, value -> value);
        assertEquals(42, saved.get("number").asNumber());
        assertEquals("flying", saved.get("text").asString());
        assertEquals(7, saved.get("new").asNumber());
        assertFalse(saved.containsKey("removed"));
        assertEquals(3, live.size());
    }

    @Test void snapshotsOnlyOptedInStateAndCopiesMutablePayloads(){
        Map<String, ArrayList<Integer>> live = new LinkedHashMap<>();
        live.put("counter:value", new ArrayList<>(java.util.List.of(12)));
        live.put("timer:running", new ArrayList<>(java.util.List.of(1)));
        live.put("variable:flight", new ArrayList<>(java.util.List.of(3)));
        Set<String> retained = Set.of("counter:value", "variable:flight");
        var saved = GraphSaveState.retain(live, retained::contains, ArrayList::new);
        assertEquals(retained, saved.keySet());
        saved.get("counter:value").set(0, 99);
        assertEquals(12, live.get("counter:value").getFirst());
        assertTrue(GraphSaveState.retain(live, key -> false, ArrayList::new).isEmpty());
    }

    @Test void booleanDirectionAndResetHaveTheSameSafeCounterSemantics(){
        assertEquals(13, GraphCounters.step(12, 1, false, false));
        assertEquals(11, GraphCounters.step(12, 1, true, false));
        assertEquals(-2.5, GraphCounters.step(0, 2.5, true, false));
        assertEquals(0, GraphCounters.step(12, 5, true, true));
        assertEquals(12, GraphCounters.step(12, Double.NaN, false, false));
        assertEquals(Double.MAX_VALUE, GraphCounters.step(Double.MAX_VALUE, Double.MAX_VALUE, false, false));
    }
}
