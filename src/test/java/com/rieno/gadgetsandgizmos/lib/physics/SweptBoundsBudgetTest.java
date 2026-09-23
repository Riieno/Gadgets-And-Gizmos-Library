package com.rieno.gadgetsandgizmos.lib.physics;

import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SweptBoundsBudgetTest{
    @BeforeAll
    static void bootstrap(){
        SableSplineConstraintTest.bootstrap();
    }

    @Test
    void missingChunksConsumeWorkInsteadOfBypassingTheScanBudget(){
        ServerLevel level = mock(ServerLevel.class);
        ServerChunkCache chunks = mock(ServerChunkCache.class);
        when(level.getChunkSource()).thenReturn(chunks);
        try(var collector = mockStatic(SubLevelBlockEntityCollector.class)){
            collector.when(() -> SubLevelBlockEntityCollector.getSubLevels(level)).thenReturn(List.of());
            var scan = SubLevelParticleOcclusion.beginSweptBoundsBlockingDistanceScan(
                    level, null, Vec3.ZERO, new Vec3(1.0D, 0.0D, 0.0D), 100_000.0D,
                    List.of(new AABB(-1.0D, 1.0D, -1.0D, 1.0D, 2.0D, 1.0D)),
                    true, Set.of(), true);
            assertFalse(scan.advance(1, 1_000_000L));
            verify(chunks, times(1)).getChunkNow(anyInt(), anyInt());
            verify(level, never()).getBlockState(any());
        }
    }

    @Test
    void completedExactSweepStillDetectsAWallUsingLoadedChunkStates(){
        ServerLevel level = mock(ServerLevel.class);
        ServerChunkCache chunks = mock(ServerChunkCache.class);
        LevelChunk chunk = mock(LevelChunk.class);
        when(level.getChunkSource()).thenReturn(chunks);
        when(chunks.getChunkNow(anyInt(), anyInt())).thenReturn(chunk);
        when(chunk.getBlockState(any())).thenAnswer(call ->
                ((BlockPos) call.getArgument(0)).getX() == 5
                        ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        try(var collector = mockStatic(SubLevelBlockEntityCollector.class)){
            collector.when(() -> SubLevelBlockEntityCollector.getSubLevels(level)).thenReturn(List.of());
            var scan = SubLevelParticleOcclusion.beginSweptBoundsBlockingDistanceScan(
                    level, null, Vec3.ZERO, new Vec3(1.0D, 0.0D, 0.0D), 10.0D,
                    List.of(new AABB(-0.5D, 1.0D, -0.5D, 0.5D, 2.0D, 0.5D)),
                    true, Set.of(), true);
            assertTrue(scan.advance(10_000, 0L));
            assertTrue(scan.result() > 4.0D && scan.result() < 4.5D);
            verify(level, never()).getBlockState(any());
        }
    }

    @Test
    void exactSweepDetectsAPartialBlockBetweenSparseFaceProbes(){
        ServerLevel level = mock(ServerLevel.class);
        ServerChunkCache chunks = mock(ServerChunkCache.class);
        LevelChunk chunk = mock(LevelChunk.class);
        when(level.getChunkSource()).thenReturn(chunks);
        when(chunks.getChunkNow(anyInt(), anyInt())).thenReturn(chunk);
        when(chunk.getBlockState(any())).thenAnswer(call -> {
            BlockPos pos = call.getArgument(0);
            return pos.getX() == 5 && pos.getY() == 1 && pos.getZ() == 0
                    ? Blocks.OAK_FENCE.defaultBlockState() : Blocks.AIR.defaultBlockState();
        });
        List<AABB> hull = List.of(new AABB(
                -0.5D, 1.0D, -3.0D, 0.5D, 2.0D, 3.0D));
        try(var collector = mockStatic(SubLevelBlockEntityCollector.class)){
            collector.when(() -> SubLevelBlockEntityCollector.getSubLevels(level)).thenReturn(List.of());
            double probed = SubLevelParticleOcclusion.findProbedBoundsBlockingDistance(
                    level, null, new Vec3(1.0D, 0.0D, 0.0D), 10.0D,
                    hull, true, Set.of(), true, 4);
            double exact = SubLevelParticleOcclusion.findSweptBoundsBlockingDistance(
                    level, null, Vec3.ZERO, new Vec3(1.0D, 0.0D, 0.0D), 10.0D,
                    hull, true, Set.of(), true, 10_000, Long.MAX_VALUE);
            assertEquals(10.0D, probed);
            assertTrue(exact > 4.0D && exact < 5.0D, "exact distance " + exact);
        }
    }

    @Test
    void contactAwareProbeAllowsTakeoffButStillBlocksMovementIntoTheGround(){
        ServerLevel level = mock(ServerLevel.class);
        ServerChunkCache chunks = mock(ServerChunkCache.class);
        LevelChunk chunk = mock(LevelChunk.class);
        when(level.getChunkSource()).thenReturn(chunks);
        when(chunks.getChunkNow(anyInt(), anyInt())).thenReturn(chunk);
        when(chunk.getBlockState(any())).thenAnswer(call ->
                ((BlockPos) call.getArgument(0)).getY() == 0
                        ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        List<AABB> hull = List.of(new AABB(0.2D, 0.95D, 0.2D, 0.8D, 1.8D, 0.8D));
        Vec3 diagonal = new Vec3(1.0D, 1.0D, 0.0D);
        try(var collector = mockStatic(SubLevelBlockEntityCollector.class)){
            collector.when(() -> SubLevelBlockEntityCollector.getSubLevels(level)).thenReturn(List.of());
            double ordinary = SubLevelParticleOcclusion.findProbedBoundsBlockingDistance(
                    level, null, diagonal, 5.0D, hull, true, Set.of(), true, 64, null);
            double takeoff = SubLevelParticleOcclusion.findProbedBoundsBlockingDistance(
                    level, null, diagonal, 5.0D, hull, true, Set.of(), true, 64, null, 0.25D);
            assertEquals(0.0D, ordinary);
            assertEquals(5.0D, takeoff);
            assertEquals(0.0D, SubLevelParticleOcclusion.findProbedBoundsBlockingDistance(
                    level, null, new Vec3(0.0D, -1.0D, 0.0D), 5.0D, hull,
                    true, Set.of(), true, 64, null, 0.25D));
            verify(level, never()).getBlockState(any());
        }
    }

    @Test
    void contactAllowanceDoesNotIgnoreDeepOverlapsOrNewWalls(){
        AABB obstacle = new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
        assertNull(SubLevelParticleOcclusion.contactAwareIntersection(
                new Vec3(0.5D, 0.95D, 0.5D), new Vec3(0.0D, 5.0D, 0.0D), obstacle, 0.1D));
        assertNotNull(SubLevelParticleOcclusion.contactAwareIntersection(
                new Vec3(0.5D, 0.5D, 0.5D), new Vec3(0.0D, 5.0D, 0.0D), obstacle, 0.1D));
        assertNotNull(SubLevelParticleOcclusion.contactAwareIntersection(
                new Vec3(0.5D, 2.0D, 0.5D), new Vec3(0.0D, -5.0D, 0.0D), obstacle, 0.1D));
    }
}
