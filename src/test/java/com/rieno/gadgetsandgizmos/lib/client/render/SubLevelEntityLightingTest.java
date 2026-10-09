package com.rieno.gadgetsandgizmos.lib.client.render;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.plot.ClientLevelPlot;
import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.loading.LoadingModList;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Exercise empty plots, extreme bounds and ordinary shading without a render-thread stall
class SubLevelEntityLightingTest{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @BeforeAll static void bootstrap(){
        SharedConstants.tryDetectVersion();
        try(var loader = mockStatic(LoadingModList.class)){
            var mods = mock(LoadingModList.class);
            when(mods.getModFiles()).thenReturn(List.of());
            loader.when(LoadingModList::get).thenReturn(mods);
            Bootstrap.bootStrap();
        }
    }

    // An unfinished native plot must not probe the level or transform sentinel coordinates
    @Test void emptyPlotsKeepWorldSkylight(){
        var body = body(BoundingBox3i.EMPTY);
        assertEquals(13, SubLevelEntityLighting.skyLight(13, new Vector3d(0, 64, 0), List.of(body)));
        verify(body, never()).getLevel();
        verify(body, never()).renderPose();
    }

    // Clamp both ends of a scan before adding or subtracting from extreme probe coordinates
    @Test void sentinelBoundsAndExtremeProbesRemainInsideTheBuildHeight(){
        var body = body(new BoundingBox3i(-1, Integer.MIN_VALUE, -1, 1, Integer.MAX_VALUE, 1));
        var level = body.getLevel();
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertEquals(15,
                SubLevelEntityLighting.skyLight(15, new Vector3d(0, Double.MAX_VALUE, 0), List.of(body))));
        verify(level, times(384)).getBlockState(any());
        clearInvocations(level);
        assertEquals(15, SubLevelEntityLighting.skyLight(15, new Vector3d(0, -Double.MAX_VALUE, 0), List.of(body)));
        verify(level, never()).getBlockState(any());
    }

    // Retain a hard budget even if a malformed level reports an unbounded vertical extent
    @Test void malformedBuildHeightsCannotCreateUnboundedScans(){
        var body = body(new BoundingBox3i(-1, Integer.MIN_VALUE, -1, 1, Integer.MAX_VALUE, 1));
        var level = body.getLevel();
        when(level.getMinBuildHeight()).thenReturn(Integer.MIN_VALUE);
        when(level.getMaxBuildHeight()).thenReturn(Integer.MAX_VALUE);
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertEquals(15,
                SubLevelEntityLighting.skyLight(15, new Vector3d(0, Double.MAX_VALUE, 0), List.of(body))));
        verify(level, times(4096)).getBlockState(any());
    }

    // Preserve Sable's skylight scaling when a real block shades the entity probe
    @Test void supportedPlotsStillShadeEntities(){
        var body = body(new BoundingBox3i(-1, 0, -1, 1, 8, 1));
        var level = body.getLevel();
        when(level.getBlockState(any())).thenAnswer(call -> ((BlockPos) call.getArgument(0)).getY() == 5
                ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        when(level.getBrightness(eq(LightLayer.SKY), any())).thenReturn(9);
        when(body.scaleSkyLight(9)).thenReturn(7);
        assertEquals(7, SubLevelEntityLighting.skyLight(15, new Vector3d(0, 7, 0), List.of(body)));
        verify(level, times(4)).getBlockState(any());
    }

    // Skip invalid render transforms before reading block states
    @Test void nonFiniteRenderPosesKeepWorldSkylight(){
        var body = body(new BoundingBox3i(-1, 0, -1, 1, 8, 1));
        var pose = new Pose3d(); pose.position().set(Double.NaN, 0, 0);
        when(body.renderPose()).thenReturn(pose);
        assertEquals(15, SubLevelEntityLighting.skyLight(15, new Vector3d(0, 7, 0), List.of(body)));
        verify(body.getLevel(), never()).getBlockState(any());
    }

    // Particle probes must also avoid empty construction plots
    @Test void unfinishedPlotsKeepParticleLighting(){
        var body = body(BoundingBox3i.EMPTY);
        int packed = net.minecraft.client.renderer.LightTexture.pack(4, 13);
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertEquals(packed,
                SubLevelEntityLighting.particleLight(packed, new Vector3d(0, 64, 0), List.of(body))));
        verify(body, never()).getLevel();
    }

    // Use the logical particle position and preserve brighter plot block light
    @Test void particlesUseLogicalPosesAndPlotBlockLight(){
        var body = body(new BoundingBox3i(-1, 0, -1, 1, 8, 1));
        when(body.logicalPose()).thenReturn(new Pose3d());
        var level = body.getLevel();
        when(level.getBlockState(any())).thenReturn(Blocks.STONE.defaultBlockState());
        when(level.getBrightness(eq(LightLayer.BLOCK), any())).thenReturn(11);
        when(level.getBrightness(eq(LightLayer.SKY), any())).thenReturn(9);
        when(body.scaleSkyLight(9)).thenReturn(7);
        int packed = net.minecraft.client.renderer.LightTexture.pack(2, 15);
        assertEquals(net.minecraft.client.renderer.LightTexture.pack(11, 7),
                SubLevelEntityLighting.particleLight(packed, new Vector3d(0, 7, 0), List.of(body)));
        verify(body, never()).renderPose();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Supply a real identity pose and an air-only native plot for bounded scan checks
    private static ClientSubLevel body(BoundingBox3ic bounds){
        var body = mock(ClientSubLevel.class);
        var plot = mock(ClientLevelPlot.class);
        var level = mock(ClientLevel.class);
        when(body.getPlot()).thenReturn(plot); when(plot.getBoundingBox()).thenReturn(bounds);
        when(body.renderPose()).thenReturn(new Pose3d()); when(body.getLevel()).thenReturn(level);
        when(level.getMinBuildHeight()).thenReturn(-64); when(level.getMaxBuildHeight()).thenReturn(320);
        when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
        return body;
    }
}
