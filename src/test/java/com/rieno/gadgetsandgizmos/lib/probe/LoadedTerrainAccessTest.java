package com.rieno.gadgetsandgizmos.lib.probe;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LoadedTerrainAccessTest{
    @BeforeAll
    static void bootstrap(){
        net.minecraft.SharedConstants.tryDetectVersion();
        try(MockedStatic<net.neoforged.fml.loading.LoadingModList> loader =
                    mockStatic(net.neoforged.fml.loading.LoadingModList.class)){
            var modList = mock(net.neoforged.fml.loading.LoadingModList.class);
            when(modList.getModFiles()).thenReturn(List.of());
            loader.when(net.neoforged.fml.loading.LoadingModList::get).thenReturn(modList);
            net.minecraft.server.Bootstrap.bootStrap();
        }
    }

    @Test
    void missingChunkNeverUsesAChunkLoadingLevelRead(){
        ServerLevel level = level();
        LoadedTerrainAccess access = new LoadedTerrainAccess();
        access.beginTick(level);
        assertFalse(access.hasSupportColumn(100.5D, 20.5D, 64.0D, 1.0D, 3.0D));
        assertNull(access.blockState(new BlockPos(100, 64, 20)));
        verify(level, never()).getBlockState(any());
        verify(level.getChunkSource(), times(1)).getChunkNow(6, 1);
    }

    @Test
    void loadedTerrainIsCachedAndRefreshedOnTheNextTick(){
        ServerLevel level = level();
        LevelChunk chunk = mock(LevelChunk.class);
        when(level.getChunkSource().getChunkNow(0, 0)).thenReturn(chunk);
        when(chunk.getBlockState(any())).thenAnswer(call ->
                ((BlockPos) call.getArgument(0)).getY() == 63
                        ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        LoadedTerrainAccess access = new LoadedTerrainAccess();
        access.beginTick(level);
        assertTrue(access.hasSupportColumn(0.5D, 0.5D, 64.0D, 1.0D, 3.0D));
        assertTrue(access.hasSupportColumn(0.5D, 0.5D, 64.0D, 1.0D, 3.0D));
        verify(level.getChunkSource(), times(1)).getChunkNow(0, 0);
        verify(level, never()).getBlockState(any());
        when(level.getGameTime()).thenReturn(1L);
        when(level.getChunkSource().getChunkNow(0, 0)).thenReturn(null);
        access.beginTick(level);
        assertFalse(access.hasSupportColumn(0.5D, 0.5D, 64.0D, 1.0D, 3.0D));
        verify(level.getChunkSource(), times(2)).getChunkNow(0, 0);
    }

    private static ServerLevel level(){
        ServerLevel level = mock(ServerLevel.class);
        when(level.getChunkSource()).thenReturn(mock(ServerChunkCache.class));
        when(level.getMinBuildHeight()).thenReturn(-64);
        when(level.getMaxBuildHeight()).thenReturn(320);
        return level;
    }
}
