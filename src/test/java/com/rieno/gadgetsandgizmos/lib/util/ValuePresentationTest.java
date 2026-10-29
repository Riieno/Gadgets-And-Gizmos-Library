package com.rieno.gadgetsandgizmos.lib.util;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.graph.GraphValueSelection;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

// Verify independent presentation baselines across edits, removals, and history overflow
class ValuePresentationTest{
    // Recover from missing baselines, skipped packets, new streams, and graph replacement
    @Test
    void orderedDeltasRequireTheDeliveredBaseline(){
        ValueUpdateSequence reader = new ValueUpdateSequence();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertFalse(reader.accept(first, 1, 2, false));
        assertTrue(reader.accept(first, 0, 1, true));
        assertTrue(reader.accept(first, 1, 2, false));
        assertFalse(reader.accept(first, 1, 2, false));
        assertFalse(reader.accept(first, 3, 4, false));
        assertTrue(reader.accept(first, 4, 5, true));
        assertFalse(reader.accept(first, 2, 3, true));
        assertFalse(reader.accept(second, 5, 6, false));
        assertTrue(reader.accept(second, 0, 1, true));
        reader.reset();
        assertFalse(reader.accept(second, 1, 2, false));
        assertTrue(reader.accept(second, 2, 3, true));
        assertFalse(reader.accept(second, -1, 0, true));
        assertFalse(reader.accept(second, 3, 5, false));
    }

    // Require a baseline for new readers and for readers whose history was discarded
    @Test
    void resetsAndOverflowRequireFullBaselines(){
        ValueChangeJournal<String> journal = new ValueChangeJournal<>(2);
        assertTrue(journal.changesSince(Long.MIN_VALUE).full());
        journal.record("a");
        long first = journal.revision();
        journal.record("b");
        journal.record("c");
        assertTrue(journal.changesSince(0).full());
        assertEquals(Set.of("b", "c"), journal.changesSince(first).keys());
        assertFalse(journal.changesSince(first).full());
        journal.reset();
        assertTrue(journal.changesSince(first).full());
        assertFalse(journal.changesSince(journal.revision()).full());
        assertTrue(journal.changesSince(journal.revision()).keys().isEmpty());
        assertTrue(journal.changesSince(journal.revision() + 1).full());
    }

    // Keep readers independent and reject null keys without advancing history
    @Test
    void readsDoNotConsumeChanges(){
        ValueChangeJournal<String> journal = new ValueChangeJournal<>(4);
        journal.record("a");
        journal.record("a");
        var first = journal.changesSince(0);
        assertEquals(first, journal.changesSince(0));
        assertEquals(Set.of("a"), first.keys());
        assertThrows(UnsupportedOperationException.class, () -> first.keys().clear());
        assertThrows(NullPointerException.class, () -> journal.record(null));
        assertEquals(2, journal.revision());
    }

    // Visit one changed key instead of scanning every live port
    @Test
    void unchangedValuesAreNotRevisited(){
        Map<String, Integer> values = new HashMap<>();
        for(int idx = 0; idx < 10000; idx++) values.put("node:" + idx, idx);
        ValueChangeJournal<String> journal = new ValueChangeJournal<>(8);
        AtomicInteger visited = new AtomicInteger();
        ValueProjection<String, Integer> projection = new ValueProjection<>(key -> {
            visited.incrementAndGet();
            return true;
        });
        projection.update(values, journal.changesSince(projection.revision()), values);
        assertEquals(10000, visited.getAndSet(0));
        values.put("node:0", 9);
        journal.record("node:0");
        var delta = projection.update(values, journal.changesSince(projection.revision()), values);
        assertEquals(Map.of("node:0", 9), delta.values());
        assertEquals(1, visited.getAndSet(0));
        assertTrue(projection.update(values, journal.changesSince(projection.revision()), values).isEmpty());
        assertEquals(0, visited.get());
    }

    // Preserve a projection when a changed value returns to its previous value before publication
    @Test
    void coalescedChangesPreserveFinalValues(){
        Map<String, Integer> values = new HashMap<>(Map.of("x", 1));
        ValueChangeJournal<String> journal = new ValueChangeJournal<>(8);
        ValueProjection<String, Integer> projection = new ValueProjection<>(key -> true);
        projection.update(values, journal.changesSince(projection.revision()), values);
        values.put("x", 2);
        journal.record("x");
        values.put("x", 1);
        journal.record("x");
        assertTrue(projection.update(values, journal.changesSince(projection.revision()), values).isEmpty());
        values.remove("x");
        journal.record("x");
        var delta = projection.update(values, journal.changesSince(projection.revision()), values);
        assertEquals(Set.of("x"), delta.removed());
        assertTrue(projection.values().isEmpty());
    }

    // Replace a baseline when a new graph has the same numerical revision
    @Test
    void sourceIdentityInvalidatesCachedValues(){
        ValueProjection<String, Integer> projection = new ValueProjection<>(key -> true);
        ValueChangeJournal<String> first = new ValueChangeJournal<>(2);
        projection.update(first, first.changesSince(projection.revision()), Map.of("old", 1));
        ValueChangeJournal<String> next = new ValueChangeJournal<>(2);
        var delta = projection.update(next, next.changesSince(projection.revision()), Map.of("new", 2));
        assertTrue(delta.full());
        assertEquals(Map.of("new", 2), projection.values());
    }

    // Reconstruct identical snapshots for readers with different cadences and interest sets
    @Test
    void independentReadersRecoverFromRandomEdits(){
        ValueChangeJournal<String> journal = new ValueChangeJournal<>(7);
        Map<String, Integer> values = new HashMap<>();
        ValueProjection<String, Integer> fast = new ValueProjection<>(key -> true);
        ValueProjection<String, Integer> slow = new ValueProjection<>(key -> key.startsWith("even:"));
        Map<String, Integer> fastClient = new HashMap<>();
        Map<String, Integer> slowClient = new HashMap<>();
        Random random = new Random(73);
        for(int idx = 0; idx < 1000; idx++){
            int node = random.nextInt(20);
            String key = (node % 2 == 0 ? "even:" : "odd:") + node;
            if(random.nextBoolean()) values.put(key, random.nextInt(100));
            else values.remove(key);
            journal.record(key);
            if(idx % 37 == 0) journal.reset();
            fast.update(values, journal.changesSince(fast.revision()), values).applyTo(fastClient);
            assertEquals(values, fastClient);
            if(idx % 13 == 0){
                slow.update(values, journal.changesSince(slow.revision()), values).applyTo(slowClient);
                Map<String, Integer> selected = new HashMap<>(values);
                selected.keySet().removeIf(port -> !port.startsWith("even:"));
                assertEquals(selected, slowClient);
            }
        }
    }

    // Match generated node ids and compound port names without losing selected properties
    @Test
    void selectionAcceptsCompoundNodeAndPortNames(){
        var selection = new GraphValueSelection(Set.of("function:instance:node"));
        assertTrue(selection.test("function:instance:node:value"));
        assertTrue(selection.test("function:instance:node:camera:element"));
        assertFalse(selection.test("function:instance:other:value"));
        assertFalse(selection.test("missing"));
    }

    // Detach updates and remove stale keys when applying full and incremental payloads
    @Test
    void deltasDetachContainersAndApplyRemovals(){
        Map<String, Integer> source = new HashMap<>(Map.of("a", 1, "b", 2));
        var baseline = ValueDelta.between(null, source);
        source.clear();
        Map<String, Integer> client = new HashMap<>(Map.of("stale", 7));
        baseline.applyTo(client);
        assertEquals(Map.of("a", 1, "b", 2), client);
        ValueDelta.between(client, Map.of("b", 3)).applyTo(client);
        assertEquals(Map.of("b", 3), client);
        assertThrows(UnsupportedOperationException.class, () -> baseline.values().clear());
        assertThrows(IllegalArgumentException.class, () -> new ValueDelta<>(false, Map.of("a", 1), Set.of("a")));
    }
}
