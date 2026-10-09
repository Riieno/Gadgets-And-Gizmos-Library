package com.rieno.gadgetsandgizmos.lib.control;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ActionGroupRoutingTest{
    @Test
    void preservesImplicitOwnershipMissingActionsAndDuplicateGroupIds(){
        var routing = new ActionGroupRouting<>(List.of(
                new ActionGroupRouting.Group<>("auto", List.of(1)),
                new ActionGroupRouting.Group<>("auto", List.of(2)),
                new ActionGroupRouting.Group<>("drive", List.of(3))),
                Map.of("auto_action", "auto", "forward", "drive", "stale", "missing"));
        assertEquals(Set.of(1), routing.firstUnitsForAction("auto_action"));
        assertEquals(Set.of(1, 2, 3), routing.unitsForExplicitActions(List.of("forward"), "auto_action"));
        assertEquals(Set.of(1, 2), routing.unitsForExplicitActions(List.of("unbound"), "auto_action"));
        assertEquals(Set.of(), routing.unitsForExplicitActions(List.of(), "auto_action"));
        assertEquals(Set.of(), routing.unitsForActions(List.of("forward", "unbound")));
        assertFalse(routing.hasGroupsFor(List.of("stale")));
        assertTrue(routing.hasBindings());
        assertThrows(UnsupportedOperationException.class,
                () -> routing.unitsForActions(List.of("forward")).clear());
    }
}
