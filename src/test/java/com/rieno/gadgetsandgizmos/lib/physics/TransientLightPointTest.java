package com.rieno.gadgetsandgizmos.lib.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Keep shared hit lights until the last camera releases them and preserve placed terrain
class TransientLightPointTest{
    // Initialize block states without requiring the game loader
    @BeforeAll
    static void bootstrap(){
        net.minecraft.SharedConstants.tryDetectVersion();
        try(var loader = mockStatic(net.neoforged.fml.loading.LoadingModList.class)){
            var mods = mock(net.neoforged.fml.loading.LoadingModList.class);
            when(mods.getModFiles()).thenReturn(List.of());
            loader.when(net.neoforged.fml.loading.LoadingModList::get).thenReturn(mods);
            net.minecraft.server.Bootstrap.bootStrap();
        }
    }
    // Removing one camera must not remove another camera's light at the same hit
    @Test
    void sharesAndMovesPointSources(){
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        Level level = level(blocks);
        var first = new TransientLightBeam(Blocks.LIGHT.defaultBlockState());
        var second = new TransientLightBeam(Blocks.LIGHT.defaultBlockState());
        BlockPos hit = new BlockPos(1, 2, 3);
        first.updatePoint(level, hit.getCenter());
        second.updatePoint(level, hit.getCenter());
        first.updatePoint(level, hit.east().getCenter());
        assertTrue(blocks.get(hit).is(Blocks.LIGHT));
        second.clear();
        assertTrue(blocks.get(hit).isAir());
        assertTrue(blocks.get(hit.east()).is(Blocks.LIGHT));
        first.clear();
        assertTrue(blocks.get(hit.east()).isAir());
        assertFalse(TransientLightBeam.isOwned(level, hit));
    }
    // A player's replacement block survives moving or disabling the flashlight
    @Test
    void neverReplacesTerrainOrFluid(){
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        Level level = level(blocks);
        BlockPos pos = new BlockPos(1, 2, 3);
        var light = new TransientLightBeam(Blocks.LIGHT.defaultBlockState());
        for(BlockState state : List.of(Blocks.STONE.defaultBlockState(), Blocks.WATER.defaultBlockState())){
            blocks.put(pos, state);
            light.updatePoint(level, pos.getCenter());
            assertSame(state, blocks.get(pos));
            assertTrue(light.positions().isEmpty());
        }
        blocks.remove(pos);
        light.updatePoint(level, pos.getCenter());
        blocks.put(pos, Blocks.STONE.defaultBlockState());
        light.clear();
        assertTrue(blocks.get(pos).is(Blocks.STONE));
    }
    // Model only loaded block access and ordinary block update notifications
    private static Level level(Map<BlockPos, BlockState> blocks){
        Level level = mock(Level.class);
        when(level.isInWorldBounds(any())).thenReturn(true);
        when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getBlockState(any())).thenAnswer(ctx -> blocks.getOrDefault(ctx.getArgument(0), Blocks.AIR.defaultBlockState()));
        when(level.setBlock(any(), any(), anyInt())).thenAnswer(ctx -> {
            blocks.put(ctx.getArgument(0), ctx.getArgument(1));
            return true;
        });
        return level;
    }
}
