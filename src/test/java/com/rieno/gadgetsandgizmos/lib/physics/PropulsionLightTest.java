package com.rieno.gadgetsandgizmos.lib.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PropulsionLightTest {
    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        try (var loader = mockStatic(net.neoforged.fml.loading.LoadingModList.class)) {
            var mods = mock(net.neoforged.fml.loading.LoadingModList.class);
            when(mods.getModFiles()).thenReturn(java.util.List.of());
            loader.when(net.neoforged.fml.loading.LoadingModList::get).thenReturn(mods);
            net.minecraft.server.Bootstrap.bootStrap();
        }
    }

    @Test
    void signedScaledThrustLightsTheSameAsPositiveThrust() {
        assertEquals(15, PropulsionLight.level(-960.0D, 960.0D));
        assertEquals(PropulsionLight.level(240.0D, 960.0D),
                PropulsionLight.level(-240.0D, 960.0D));
        assertEquals(0, PropulsionLight.level(0.0D, 960.0D));
    }

    @Test
    void disabledClientEmissionLeavesTheSyncedLightValueAvailable() {
        assertEquals(0, PropulsionLight.emission(15, false));
        assertEquals(15, PropulsionLight.emission(15, true));
        assertEquals(0, PropulsionLight.emission(0, true));
    }

    @Test
    void changedLightIsPublishedAsABlockStateUpdate() {
        Level world = mock(Level.class);
        BlockPos pos = new BlockPos(1, 64, 2);
        BlockState oldState = mock(BlockState.class);
        BlockState litState = mock(BlockState.class);
        when(world.getBlockState(pos)).thenReturn(oldState);
        when(oldState.hasProperty(PropulsionLight.LIGHT_LEVEL)).thenReturn(true);
        when(oldState.getValue(PropulsionLight.LIGHT_LEVEL)).thenReturn(0);
        when(oldState.setValue(PropulsionLight.LIGHT_LEVEL, 15)).thenReturn(litState);
        when(world.setBlock(pos, litState, Block.UPDATE_CLIENTS)).thenReturn(true);

        assertTrue(PropulsionLight.update(world, pos, 15));
        verify(world).setBlock(pos, litState, Block.UPDATE_CLIENTS);
    }
}
