package com.rieno.gadgetsandgizmos.lib.create;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectedMultiblockPlacementTest {
    @Test
    void buildsVaultCrossSectionAtTheSelectedEnd() {
        List<BlockPos> positions = ConnectedMultiblockPlacement.vaultExtension(
                BlockPos.ZERO, Direction.Axis.Z, 2, 4, Direction.SOUTH);

        assertEquals(4, positions.size());
        assertTrue(positions.contains(new BlockPos(0, 0, 4)));
        assertTrue(positions.contains(new BlockPos(1, 1, 4)));
    }

    @Test
    void buildsTankLayerBelowTheController() {
        List<BlockPos> positions = ConnectedMultiblockPlacement.tankExtension(
                new BlockPos(3, 5, 7), 3, 6, Direction.DOWN);

        assertEquals(9, positions.size());
        assertTrue(positions.contains(new BlockPos(3, 4, 7)));
        assertTrue(positions.contains(new BlockPos(5, 4, 9)));
    }
}
