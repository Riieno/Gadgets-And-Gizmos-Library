package com.rieno.gadgetsandgizmos.lib.scm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScmSubLevelRelationRegistryTest {
    @Test
    void resolvesLinksWhenTheJointReportsTheRootAsItsCompanion() {
        UUID root = UUID.randomUUID();
        UUID upperLeg = UUID.randomUUID();
        UUID lowerLeg = UUID.randomUUID();
        List<ScmSubLevelRelationRegistry.Relation> relations = List.of(
                new ScmSubLevelRelationRegistry.Relation(upperLeg, root, "test:joint"),
                new ScmSubLevelRelationRegistry.Relation(lowerLeg, upperLeg, "test:joint"));

        assertEquals(Set.of(root, upperLeg, lowerLeg),
                ScmSubLevelRelationRegistry.connected(List.of(root), relations));
    }

    @Test
    void identifiesTheRelationFromItsOwningJointBody() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        var relation = new ScmSubLevelRelationRegistry.Relation(first, second,
                "test:joint", owner, new net.minecraft.core.BlockPos(4, 5, 6));

        assertEquals(true, relation.matchesSource(owner, new net.minecraft.core.BlockPos(4, 5, 6)));
        assertEquals(false, relation.matchesSource(first, new net.minecraft.core.BlockPos(4, 5, 6)));
    }
}
