package com.rieno.gadgetsandgizmos.lib.graph;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class GraphRuntimeJournalTest{
    @Test
    void repeatedPulsesKeepExecutionOrderAndPlayer(){
        var journal = new GraphRuntimeJournal<String>(16);
        var reader = journal.read(null, 0);
        UUID player = UUID.randomUUID();
        journal.value(GraphRuntimeJournal.Channel.OUTPUT, "counter:value", "1", 20, 3, player);
        journal.pulse("wire", 20, 3, player);
        journal.pulse("wire", 20, 3, player);
        journal.value(GraphRuntimeJournal.Channel.OUTPUT, "counter:value", "2", 20, 3, player);
        var update = journal.read(reader.streamId(), reader.sequence());
        assertFalse(update.reset());
        assertEquals(4, update.events().size());
        assertEquals(GraphRuntimeJournal.Channel.PULSE, update.events().get(1).channel());
        assertEquals(GraphRuntimeJournal.Channel.PULSE, update.events().get(2).channel());
        assertEquals(player, update.events().get(2).playerId());
        assertEquals(3, update.events().get(2).revision());
        assertEquals("2", update.events().get(3).value());
        assertEquals(update, journal.read(reader.streamId(), reader.sequence()));
    }

    @Test
    void deletionAndResetCannotLeaveOldPorts(){
        var journal = new GraphRuntimeJournal<String>(2);
        journal.value(GraphRuntimeJournal.Channel.INPUT, "old", "value", 1, 0, null);
        var reader = journal.read(null, 0);
        journal.value(GraphRuntimeJournal.Channel.INPUT, "old", null, 2, 0, null);
        assertNull(journal.read(reader.streamId(), reader.sequence()).events().getFirst().value());
        journal.pulse("a", 2, 0, null);
        journal.pulse("b", 2, 0, null);
        assertTrue(journal.read(reader.streamId(), reader.sequence()).reset());
        assertTrue(journal.read(reader.streamId(), reader.sequence()).events().isEmpty());
        journal.clear();
        assertTrue(journal.read(reader.streamId(), 0).reset());
    }
}
