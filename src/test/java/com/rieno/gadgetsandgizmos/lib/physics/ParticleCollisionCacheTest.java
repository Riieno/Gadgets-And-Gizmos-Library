package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
}
