package com.rieno.gadgetsandgizmos.lib.graph;

import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GraphTargetSelectionTest{
    @Test void differentFacesRemainDistinctAndLegacyFaceIdsRemainSelectable(){
        var target = target();
        var north = GraphTargetSelection.withFace(target, Direction.NORTH);
        var south = GraphTargetSelection.withFace(target, Direction.SOUTH);
        assertNotEquals(north.nodeId(), south.nodeId());
        assertTrue(north.hasSameBlockTarget(south));
        assertEquals(north.nodeId(), GraphTargetSelection.withFace(north, Direction.NORTH).nodeId());
        assertEquals(south.nodeId(), GraphTargetSelection.withFace(north, Direction.SOUTH).nodeId());
        var selections = List.of(target, south);
        var faces = Map.of(target.nodeId(), Direction.NORTH, south.nodeId(), Direction.SOUTH);
        assertEquals(target.nodeId(), GraphTargetSelection.matchingFace(selections, faces, north, Direction.NORTH));
        assertEquals(south.nodeId(), GraphTargetSelection.matchingFace(selections, faces, north, Direction.SOUTH));
        assertNull(GraphTargetSelection.matchingFace(selections, faces, north, Direction.UP));
        var moved = north.withLocation(north.subLevelId(), new BlockPos(8, 5, 3));
        assertNull(GraphTargetSelection.matchingFace(selections, faces, moved, Direction.NORTH));
    }

    @Test void aliasesChangeGroupNamesWithoutChangingPortsOrBindings(){
        var target = target();
        var north = GraphTargetSelection.withFace(target, Direction.NORTH);
        var south = GraphTargetSelection.withFace(target, Direction.SOUTH);
        var ports = List.of(new GraphTargetPortLayout.Port("signal", "Signal", "number"));
        var before = GraphTargetPortLayout.compose(List.of(
                new GraphTargetPortLayout.Target(north.nodeId(), GraphTargetSelection.label(north, Direction.NORTH, ""), ports),
                new GraphTargetPortLayout.Target(south.nodeId(), GraphTargetSelection.label(south, Direction.SOUTH, ""), ports)), false);
        var after = GraphTargetPortLayout.compose(List.of(
                new GraphTargetPortLayout.Target(north.nodeId(), GraphTargetSelection.label(north, Direction.NORTH, " Intake "), ports),
                new GraphTargetPortLayout.Target(south.nodeId(), GraphTargetSelection.label(south, Direction.SOUTH, "Exhaust"), ports)), false);
        assertEquals(before.ports(), after.ports());
        assertEquals(before.bindings(), after.bindings());
        assertEquals(List.of("Intake", "Exhaust"), after.sections().stream().map(GraphTargetPortLayout.Section::label).toList());
        assertEquals(2, after.ports().size());
        assertEquals(north.nodeId(), ControllerDiscoveryNode.fromTag(north.toTag()).nodeId());
        assertEquals("", GraphTargetSelection.normalizeAlias(null));
        assertEquals("Intake", GraphTargetSelection.normalizeAlias(" Intake\n"));
        assertEquals(64, GraphTargetSelection.normalizeAlias("x".repeat(100)).length());
    }

    private static ControllerDiscoveryNode target(){
        UUID ship = UUID.randomUUID();
        return new ControllerDiscoveryNode("scm_picker:" + ship + ":0", ControllerDiscoveryKind.MACHINE,
                "scm_picker", "minecraft:stone", "Valve", ship, BlockPos.ZERO);
    }
}
