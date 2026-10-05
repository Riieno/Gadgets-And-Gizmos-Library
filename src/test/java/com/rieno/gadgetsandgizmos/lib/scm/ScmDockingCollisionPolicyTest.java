package com.rieno.gadgetsandgizmos.lib.scm;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ScmDockingCollisionPolicyTest{
    @Test
    void onlyTheSelectedDockBodyJoinsOwnCollisionExclusions(){
        UUID ship = UUID.randomUUID();
        UUID selectedDock = UUID.randomUUID();
        UUID otherShip = UUID.randomUUID();
        Set<UUID> excluded = ScmDockingCollisionPolicy.excludedSubLevels(
                Set.of(ship), Set.of(selectedDock), true);
        assertEquals(Set.of(ship, selectedDock), excluded);
        assertFalse(excluded.contains(otherShip));
        assertEquals(Set.of(ship), ScmDockingCollisionPolicy.excludedSubLevels(
                Set.of(ship), Set.of(selectedDock), false));
    }

    @Test
    void dockingSeesOnlyOtherProtectedBodies(){
        UUID own = UUID.randomUUID();
        UUID dock = UUID.randomUUID();
        UUID otherShip = UUID.randomUUID();
        UUID uncontrolledBody = UUID.randomUUID();
        Set<UUID> excluded = ScmDockingCollisionPolicy.excludedExceptProtected(
                Set.of(own), Set.of(dock),
                Set.of(own, dock, otherShip, uncontrolledBody),
                Set.of(own, dock, otherShip));
        assertEquals(Set.of(own, dock, uncontrolledBody), excluded);
        assertFalse(excluded.contains(otherShip));
    }
}
