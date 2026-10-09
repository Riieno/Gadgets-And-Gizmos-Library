package com.rieno.gadgetsandgizmos.lib.client.render;

import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.plot.ClientLevelPlot;
import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Exercise the rain scan that runs while construction bodies have no valid plot bounds
class SubLevelWeatherHeightTest{
    @BeforeAll static void bootstrap(){
        SharedConstants.tryDetectVersion();
        try(var loader = mockStatic(LoadingModList.class)){
            var mods = mock(LoadingModList.class);
            when(mods.getModFiles()).thenReturn(List.of());
            loader.when(LoadingModList::get).thenReturn(mods);
            Bootstrap.bootStrap();
        }
    }

    // Reject unfinished plots before reading their sentinel world bounds or transforms
    @Test void emptyPlotsNeverEnterTheRainScan(){
        var body = body(BoundingBox3i.EMPTY);
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertEquals(Integer.MIN_VALUE,
                SubLevelWeatherHeight.rainHeight(body.getLevel(), 0, 2, 0, List.of(body))));
        verify(body, never()).boundingBox(); verify(body, never()).logicalPose();
        verify(body.getLevel(), never()).getBlockState(any());
    }

    // Clamp finite extreme bounds before stepping or converting them to block coordinates
    @Test void extremeBoundsRespectTheWorldBuildHeight(){
        var body = body(new BoundingBox3i(-1, Integer.MIN_VALUE, -1, 1, Integer.MAX_VALUE, 1));
        when(body.boundingBox()).thenReturn(new BoundingBox3d(-1, -Double.MAX_VALUE, -1, 1, Double.MAX_VALUE, 1));
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertEquals(Integer.MIN_VALUE,
                SubLevelWeatherHeight.rainHeight(body.getLevel(), 0, 2, 0, List.of(body))));
        verify(body.getLevel(), times(384)).getBlockState(any());
    }

    // Retain a hard budget even when a level supplies malformed build heights
    @Test void malformedBuildHeightsCannotOverflowTheLoop(){
        var body = body(new BoundingBox3i(-1, Integer.MIN_VALUE, -1, 1, Integer.MAX_VALUE, 1));
        when(body.boundingBox()).thenReturn(new BoundingBox3d(-1, Integer.MIN_VALUE, -1, 1, Integer.MAX_VALUE, 1));
        when(body.getLevel().getMinBuildHeight()).thenReturn(Integer.MIN_VALUE);
        when(body.getLevel().getMaxBuildHeight()).thenReturn(Integer.MAX_VALUE);
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertEquals(Integer.MIN_VALUE,
                SubLevelWeatherHeight.rainHeight(body.getLevel(), 0, 2, 0, List.of(body))));
        verify(body.getLevel(), times(4096)).getBlockState(any());
    }

    // Preserve solid roofs and fluid surfaces in both splash and rain rendering probes
    @Test void loadedRoofsAndFluidsStillStopRain(){
        var body = body(new BoundingBox3i(-1, 0, -1, 1, 8, 1));
        var level = body.getLevel();
        when(body.boundingBox()).thenReturn(new BoundingBox3d(-1, 0, -1, 1, 9, 1));
        when(level.getBlockState(any())).thenAnswer(call -> ((BlockPos) call.getArgument(0)).getY() == 5
                ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        assertEquals(7, SubLevelWeatherHeight.rainHeight(level, 0, 2, 0, List.of(body)));
        doReturn(Blocks.WATER.defaultBlockState()).when(level).getBlockState(any());
        assertEquals(9, SubLevelWeatherHeight.rainHeight(level, 0, 1, 0, List.of(body)));
        when(level.hasChunkAt(any())).thenReturn(false);
        clearInvocations(level);
        assertEquals(Integer.MIN_VALUE, SubLevelWeatherHeight.rainHeight(level, 0, 1, 0, List.of(body)));
        verify(level, never()).getBlockState(any());
    }

    // Skip invalid world bounds and poses without probing plot blocks
    @Test void invalidTransformsDoNotReachBlockReads(){
        var body = body(new BoundingBox3i(-1, 0, -1, 1, 8, 1));
        when(body.boundingBox()).thenReturn(new BoundingBox3d(-1, Double.NaN, -1, 1, 9, 1));
        assertEquals(Integer.MIN_VALUE, SubLevelWeatherHeight.rainHeight(body.getLevel(), 0, 1, 0, List.of(body)));
        when(body.boundingBox()).thenReturn(new BoundingBox3d(-1, 0, -1, 1, 9, 1));
        var pose = new Pose3d(); pose.position().set(Double.NaN, 0, 0); when(body.logicalPose()).thenReturn(pose);
        assertEquals(Integer.MIN_VALUE, SubLevelWeatherHeight.rainHeight(body.getLevel(), 0, 1, 0, List.of(body)));
        verify(body.getLevel(), never()).getBlockState(any());
    }

    // Supply a loaded native plot with a real identity transform
    private static ClientSubLevel body(BoundingBox3ic bounds){
        var body = mock(ClientSubLevel.class); var plot = mock(ClientLevelPlot.class); var level = mock(ClientLevel.class);
        when(body.getPlot()).thenReturn(plot); when(plot.getBoundingBox()).thenReturn(bounds);
        when(body.getLevel()).thenReturn(level); when(body.logicalPose()).thenReturn(new Pose3d());
        when(level.getMinBuildHeight()).thenReturn(-64); when(level.getMaxBuildHeight()).thenReturn(320);
        when(level.hasChunkAt(any())).thenReturn(true); when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
        return body;
    }
}
