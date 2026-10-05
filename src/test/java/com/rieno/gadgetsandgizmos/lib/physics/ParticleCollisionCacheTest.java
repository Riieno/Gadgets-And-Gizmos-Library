package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.navigation.SablePathfinder;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Check collision accuracy and shared lookup costs for particle sweeps
class ParticleCollisionCacheTest{
    // Load vanilla block collision shapes
    @BeforeAll
    static void bootstrap(){
        SableSplineConstraintTest.bootstrap();
    }

    // Repeated particles reuse loaded shapes and see block edits after cache refresh
    @Test
    void sharedSweepsReuseShapesAndRefreshRemovedWalls(){
        ServerLevel level = mock(ServerLevel.class);
        ServerChunkCache chunks = mock(ServerChunkCache.class);
        LevelChunk chunk = mock(LevelChunk.class);
        AtomicBoolean wall = new AtomicBoolean(true);
        when(level.getChunkSource()).thenReturn(chunks);
        when(chunks.getChunkNow(anyInt(), anyInt())).thenReturn(chunk);
        when(chunk.getBlockState(any())).thenAnswer(call ->
                wall.get() && ((BlockPos) call.getArgument(0)).getX() == 2
                        ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        var cache = new SubLevelParticleOcclusion.ProbeCache();
        Vec3 start = new Vec3(0.5D, 0.5D, 0.5D);
        Vec3 dir = new Vec3(1.0D, 0.0D, 0.0D);
        try(var collector = mockStatic(SubLevelBlockEntityCollector.class)){
            collector.when(() -> SubLevelBlockEntityCollector.getSubLevels(level)).thenReturn(List.of());
            for(int idx = 0; idx < 100; idx++){
                double distance = SubLevelParticleOcclusion.findBlockingDistance(
                        level, null, start, dir, 4.0D, true, Set.of(), true, cache);
                assertTrue(distance > 1.4D && distance < 1.5D);
            }
            verify(chunk, times(3)).getBlockState(any());
            verify(chunks, times(1)).getChunkNow(anyInt(), anyInt());
            collector.verify(() -> SubLevelBlockEntityCollector.getSubLevels(level), times(1));
            wall.set(false);
            cache.clear();
            assertEquals(4.0D, SubLevelParticleOcclusion.findBlockingDistance(
                    level, null, start, dir, 4.0D, true, Set.of(), true, cache));
            verify(level, never()).getBlockState(any());
        }
    }

    // Sable sweeps use current rotation and translation even with cached local block shapes
    @Test
    void rotatedSubLevelUsesItsCurrentPose(){
        ServerLevel level = mock(ServerLevel.class);
        ServerChunkCache chunks = mock(ServerChunkCache.class);
        LevelChunk chunk = mock(LevelChunk.class);
        when(level.getChunkSource()).thenReturn(chunks);
        when(chunks.getChunkNow(anyInt(), anyInt())).thenReturn(chunk);
        when(chunk.getBlockState(any())).thenAnswer(call -> {
            BlockPos pos = call.getArgument(0);
            return pos.getX() == 2 && pos.getY() == 0 && pos.getZ() == 0
                    ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
        });
        ServerSubLevel body = mock(ServerSubLevel.class);
        Pose3d pose = new Pose3d();
        pose.position().set(100.0D, 20.0D, 100.0D);
        pose.orientation().rotateY(Math.PI * 0.5D);
        when(body.getLevel()).thenReturn(level);
        when(body.getUniqueId()).thenReturn(UUID.randomUUID());
        when(body.logicalPose()).thenReturn(pose);
        when(body.boundingBox()).thenReturn(new BoundingBox3d(
                new AABB(90.0D, 10.0D, 90.0D, 110.0D, 30.0D, 110.0D)));
        var cache = new SubLevelParticleOcclusion.ProbeCache();
        Vec3 start = pose.transformPosition(new Vec3(0.5D, 0.5D, 0.5D));
        Vec3 dir = pose.transformNormal(new Vec3(1.0D, 0.0D, 0.0D));
        try(var collector = mockStatic(SubLevelBlockEntityCollector.class)){
            collector.when(() -> SubLevelBlockEntityCollector.getSubLevels(level)).thenReturn(List.of(body));
            double distance = SubLevelParticleOcclusion.findBlockingDistance(
                    level, null, start, dir, 4.0D, true, Set.of(), true, cache);
            assertTrue(distance > 1.4D && distance < 1.5D);
            pose.position().add(5.0D, 0.0D, 0.0D);
            assertEquals(4.0D, SubLevelParticleOcclusion.findBlockingDistance(
                    level, null, start, dir, 4.0D, true, Set.of(), true, cache));
        }
    }

    @Test
    void compoundSideClearanceIgnoresEachSupportHeightAndOverheadContact(){
        ServerLevel level = mock(ServerLevel.class);
        ServerChunkCache chunks = mock(ServerChunkCache.class);
        LevelChunk chunk = mock(LevelChunk.class);
        AtomicBoolean wall = new AtomicBoolean(false);
        when(level.getChunkSource()).thenReturn(chunks);
        when(chunks.getChunkNow(anyInt(), anyInt())).thenReturn(chunk);
        when(chunk.getBlockState(any())).thenAnswer(call -> {
            BlockPos pos = call.getArgument(0);
            boolean support = pos.getY() == (pos.getX() < 4 ? 0 : 2);
            boolean overhead = pos.getY() == (pos.getX() < 4 ? 3 : 5);
            boolean blocker = wall.get() && pos.getX() == 9 && pos.getY() == 3;
            return support || overhead || blocker ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
        });
        var bounds = SubLevelParticleOcclusion.sideClearanceBounds(List.of(
                new AABB(0, 1, 0, 2, 3, 2), new AABB(5, 3, 0, 7, 5, 2)), 0.15D, 0.25D);
        try(var collector = mockStatic(SubLevelBlockEntityCollector.class)){
            collector.when(() -> SubLevelBlockEntityCollector.getSubLevels(level)).thenReturn(List.of());
            assertEquals(2.0D, SubLevelParticleOcclusion.findSweptBoundsBlockingDistance(
                    level, null, Vec3.ZERO, new Vec3(0, 0, 1), 2, bounds, true, Set.of(), true,
                    100_000, 1_000_000_000L));
            wall.set(true);
            double clearance = SubLevelParticleOcclusion.findSweptBoundsBlockingDistance(
                    level, null, Vec3.ZERO, new Vec3(1, 0, 0), 4, bounds.subList(1, 2), true, Set.of(), true,
                    100_000, 1_000_000_000L);
            assertTrue(clearance < 2.0D, "Side obstacle clearance: " + clearance);
        }
    }

    @Test
    void unfinishedFullHullScanDoesNotInventAnImmediateCollision(){
        ServerLevel level = mock(ServerLevel.class);
        ServerChunkCache chunks = mock(ServerChunkCache.class);
        LevelChunk chunk = mock(LevelChunk.class);
        when(level.getChunkSource()).thenReturn(chunks);
        when(chunks.getChunkNow(anyInt(), anyInt())).thenReturn(chunk);
        when(chunk.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
        try(var collector = mockStatic(SubLevelBlockEntityCollector.class)){
            collector.when(() -> SubLevelBlockEntityCollector.getSubLevels(level)).thenReturn(List.of());
            var scan = SubLevelParticleOcclusion.beginSweptBoundsBlockingDistanceScan(level, null,
                    Vec3.ZERO, new Vec3(1, 0, 0), 8, List.of(new AABB(-20, 0, -3, 20, 4, 3)), true, Set.of(), true);
            assertFalse(scan.advance(1, 1));
            assertEquals(8.0D, scan.confirmedBlockingDistance(8.0D));
            assertTrue(scan.advance(100_000, 1_000_000_000L));
            assertEquals(8.0D, scan.confirmedBlockingDistance(8.0D));
        }
    }

    @Test
    void routeValidationUsesTheTailAndIgnoresGroundAndCeiling(){
        ServerLevel level = mock(ServerLevel.class);
        when(level.isLoaded(any())).thenReturn(true);
        when(level.getBlockState(any())).thenAnswer(call -> {
            BlockPos pos = call.getArgument(0);
            boolean wall = pos.getZ() == -15 && pos.getY() == 0;
            return wall || pos.getY() < 0 || pos.getY() >= 2
                    ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
        });
        List<AABB> bounds = SubLevelParticleOcclusion.sideClearanceBounds(List.of(
                new AABB(-1, 0, -2, 1, 2, 2), new AABB(-1, 0, -13, 1, 2, -9)), 0.15D, 0.25D);
        try(var collector = mockStatic(SubLevelBlockEntityCollector.class);
            var transforms = mockStatic(SableTransformApi.class)){
            collector.when(() -> SubLevelBlockEntityCollector.getSubLevels(level)).thenReturn(List.of());
            collector.when(() -> SubLevelBlockEntityCollector.isTargetLoaded(same(level), isNull(), any())).thenReturn(true);
            transforms.when(() -> SableTransformApi.intersecting(same(level), any(AABB.class))).thenReturn(List.of());
            var validator = SablePathfinder.compoundCollisionValidator(SablePathfinder.CollisionOptions.DEFAULT,
                    query -> bounds.stream().map(box -> box.move(query.start())).toList());
            var safety = new SablePathfinder.Safety(1, 2, 0);
            assertEquals(SablePathfinder.TraversalResult.CLEAR, validator.validate(new SablePathfinder.Query(
                    level, Vec3.ZERO, new Vec3(0, 0, 4), safety, SablePathfinder.RouteMode.GROUND)).result());
            assertEquals(SablePathfinder.TraversalResult.BLOCKED, validator.validate(new SablePathfinder.Query(
                    level, Vec3.ZERO, new Vec3(0, 0, -4), safety, SablePathfinder.RouteMode.GROUND)).result());
        }
    }
}
