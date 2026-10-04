package com.rieno.gadgetsandgizmos.lib.probe;

import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockStateDataAccessTest {
    @Test
    void statePropertiesRemainReadableWithoutBecomingWritable() {
        var state = Blocks.LEVER.defaultBlockState();

        assertTrue(BlockStateDataAccess.data(state, false).containsKey("state_powered"));
        assertFalse(BlockStateDataAccess.data(state, true).containsKey("state_powered"));
        assertFalse(BlockStateDataAccess.options(state, true).containsKey("state_face"));
        assertTrue(BlockStateDataAccess.data(Blocks.REDSTONE_WIRE.defaultBlockState(), true)
                .containsKey("state_power"));
    }
}
