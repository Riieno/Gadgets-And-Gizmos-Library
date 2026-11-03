package com.rieno.gadgetsandgizmos.lib.graph;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class GraphInputEventsTest{
    @Test
    void closingOneRegistrationDoesNotRemoveAnotherForTheSameReceiver(){
        var source = new GraphInputEvents<String>(err -> fail(err));
        var events = new ArrayList<GraphInputEvents.Event<String>>();
        java.util.function.Consumer<GraphInputEvents.Event<String>> receiver = events::add;
        var first = source.subscribe(receiver);
        var second = source.subscribe(receiver);
        first.close();
        first.close();
        source.publish("input", "value", null);
        assertEquals(1, events.size());
        second.close();
        source.publish("input", "value", null);
        assertEquals(1, events.size());
    }

    @Test
    void independentObserversKeepRepeatedInputsAndCloseWithoutStoppingTheSource(){
        var failures = new AtomicInteger();
        var source = new GraphInputEvents<String>(err -> failures.incrementAndGet());
        var first = new ArrayList<GraphInputEvents.Event<String>>();
        var second = new ArrayList<GraphInputEvents.Event<String>>();
        var subscription = source.subscribe(first::add);
        source.subscribe(evt -> { throw new IllegalArgumentException(); });
        source.subscribe(second::add);
        UUID player = UUID.randomUUID();
        source.publish("named:test", "value", player);
        source.publish("named:test", "value", player);
        subscription.close();
        subscription.close();
        source.publish("named:test", "value", player);
        assertEquals(2, first.size());
        assertEquals(3, second.size());
        assertEquals(3, failures.get());
        assertEquals(player, second.getFirst().playerId());
        assertEquals(first.getFirst(), second.getFirst());
    }
}
