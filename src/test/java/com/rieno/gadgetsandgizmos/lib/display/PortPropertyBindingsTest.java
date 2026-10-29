package com.rieno.gadgetsandgizmos.lib.display;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PortPropertyBindingsTest{
    @Test
    void defaultClearsOnlyTheSelectedPort(){
        Map<String, String> prev = Map.of("X", "position", "Y", "position", "Color", "color");
        assertEquals(Map.of("Color", "color"), PortPropertyBindings.assign(prev, "position", ""));
        assertEquals(3, prev.size());
    }

    @Test
    void reassignmentMovesThePropertyAndRetainsOtherBindings(){
        assertEquals(Map.of("X", "color", "Y", "position"), PortPropertyBindings.assign(
                Map.of("X", "position", "Y", "position", "Color", "color"), "color", "X"));
    }

    @Test
    void existingSharedBindingsRemainVisible(){
        assertEquals(2, PortPropertyBindings.properties(Map.of("X", "position", "Y", "position"), "position").size());
        assertTrue(PortPropertyBindings.properties(Map.of("X", "position"), "").isEmpty());
    }
}
