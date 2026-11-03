package com.rieno.gadgetsandgizmos.lib.graph;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static com.rieno.gadgetsandgizmos.lib.graph.GraphRuntimeJournal.Channel.*;
import static org.junit.jupiter.api.Assertions.*;

class GraphRuntimeReplicaTest{
    @Test
    void gapsRequireResetAndRepeatedPulsesRemainDistinct(){
        var replica = new GraphRuntimeReplica<Integer>();
        var pulses = new ArrayList<GraphRuntimeJournal.Event<Integer>>();
        UUID stream = UUID.randomUUID();
        var initial = new GraphRuntimeJournal.Update<Integer>(stream, 3, true, List.of(
                new GraphRuntimeJournal.Event<>(1, 0, 0, null, OUTPUT, "value", 10)));
        assertTrue(replica.accept(0, initial, pulses::add));
        var delta = new GraphRuntimeJournal.Update<Integer>(stream, 5, false, List.of(
                new GraphRuntimeJournal.Event<>(4, 20, 1, null, PULSE, "edge", null),
                new GraphRuntimeJournal.Event<>(5, 20, 1, null, PULSE, "edge", null)));
        assertFalse(replica.accept(4, delta, pulses::add));
        assertTrue(replica.accept(3, delta, pulses::add));
        assertFalse(replica.accept(3, delta, pulses::add));
        assertEquals(2, pulses.size());
        assertEquals(10, replica.values(OUTPUT).get("value"));
        assertTrue(replica.accept(5, new GraphRuntimeJournal.Update<>(stream, 6, false, List.of(
                new GraphRuntimeJournal.Event<Integer>(6, 20, 1, null, OUTPUT, "value", null))), pulses::add));
        assertTrue(replica.values(OUTPUT).isEmpty());
        replica.clear();
        assertFalse(replica.accept(3, delta, pulses::add));
        assertTrue(replica.accept(0, initial, pulses::add));
    }
}
