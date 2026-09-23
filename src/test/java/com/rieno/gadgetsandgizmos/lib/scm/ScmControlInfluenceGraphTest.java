package com.rieno.gadgetsandgizmos.lib.scm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ScmControlInfluenceGraphTest{
    @Test
    void independentControlStopsTraversalIntoItsOwnOutputs(){
        var graph = Map.of("control", List.of("shaft"), "shaft", List.of("rotor", "other-control", "control"),
                "other-control", List.of("other-rotor"));
        var search = new ScmControlInfluenceGraph.Search<>("control", node -> graph.getOrDefault(node, List.of()),
                node -> node.endsWith("rotor"), "other-control"::equals, 64);
        search.advance(1, 1_000_000_000L);
        assertFalse(search.complete());
        search.advance(64, 1_000_000_000L);
        assertTrue(search.complete());
        assertEquals(List.of("rotor"), search.outputs());
    }

    @Test
    void largeNetworkCannotExceedItsNodeBudgetOrBeMistakenForACompleteMapping(){
        AtomicInteger calls = new AtomicInteger();
        var search = new ScmControlInfluenceGraph.Search<>(0, node -> {
            calls.incrementAndGet();
            return List.of(node + 1);
        }, node -> true, node -> false, 100);
        search.advance(3, 1_000_000_000L);
        assertEquals(3, calls.get());
        search.advance(1000, 1_000_000_000L);
        assertEquals(100, calls.get());
        assertTrue(search.finished());
        assertFalse(search.complete());
        assertEquals(100, search.outputs().size());
    }

    @Test
    void zeroTimeOrWorkDoesNotTouchTheNetwork(){
        AtomicInteger calls = new AtomicInteger();
        var search = new ScmControlInfluenceGraph.Search<>(0, node -> {
            calls.incrementAndGet();
            return List.of();
        }, node -> false, node -> false, 100);
        search.advance(100, 0L);
        search.advance(0, 1_000_000_000L);
        assertEquals(0, calls.get());
        assertFalse(search.finished());
    }
}
