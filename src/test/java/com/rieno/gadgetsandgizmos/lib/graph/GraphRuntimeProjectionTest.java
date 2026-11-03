package com.rieno.gadgetsandgizmos.lib.graph;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import static com.rieno.gadgetsandgizmos.lib.graph.GraphRuntimeJournal.Channel.*;
import static org.junit.jupiter.api.Assertions.*;

class GraphRuntimeProjectionTest{
    @Test
    void overridesRestoreCurrentSharedValuesWithoutRetainingSharedMaps(){
        var journal = new GraphRuntimeJournal<Integer>(10);
        var projection = new GraphRuntimeProjection<Integer>();
        journal.value(OUTPUT, "value", 1, 1, 1, null);
        var first = projection.project(journal.read(null, 0), Map.of(OUTPUT, Map.of("value", 10)),
                (channel, key) -> 1, false);
        assertTrue(first.reset());
        assertEquals(10, first.events().getFirst().value());
        journal.value(OUTPUT, "value", 2, 2, 1, null);
        var second = projection.project(journal.read(projection.sourceStream(), projection.sourceSequence()),
                Map.of(OUTPUT, Map.of("value", 10)), (channel, key) -> 2, false);
        assertTrue(second.events().isEmpty());
        assertEquals(first.sequence(), second.sequence());
        var third = projection.project(journal.read(projection.sourceStream(), projection.sourceSequence()),
                Map.of(), (channel, key) -> 2, false);
        assertEquals(2, third.events().getFirst().value());
    }

    @Test
    void repeatedPulsesAndMetadataUpdatesHaveIndependentSequences(){
        var journal = new GraphRuntimeJournal<Integer>(10);
        var projection = new GraphRuntimeProjection<Integer>();
        var first = projection.project(journal.read(null, 0), Map.of(), (channel, key) -> null, false);
        UUID player = UUID.randomUUID();
        journal.pulse("wire", 3, 1, player);
        journal.pulse("wire", 3, 1, player);
        var next = projection.project(journal.read(projection.sourceStream(), projection.sourceSequence()),
                Map.of(), (channel, key) -> null, false);
        assertEquals(2, next.events().size());
        assertEquals(player, next.events().getFirst().playerId());
        assertEquals(3, next.events().getLast().tick());
        assertTrue(next.sequence() > first.sequence());
        var metadata = projection.project(journal.read(projection.sourceStream(), projection.sourceSequence()),
                Map.of(), (channel, key) -> null, true);
        assertEquals(next.sequence() + 1, metadata.sequence());
        journal.clear();
        var reset = projection.project(journal.read(projection.sourceStream(), projection.sourceSequence()),
                Map.of(), (channel, key) -> null, false);
        assertTrue(reset.reset());
        assertNotEquals(metadata.streamId(), reset.streamId());
    }
}
