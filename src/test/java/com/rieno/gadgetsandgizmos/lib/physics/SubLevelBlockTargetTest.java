package com.rieno.gadgetsandgizmos.lib.physics;

import com.rieno.gadgetsandgizmos.lib.control.ControllerDirectTargetReference;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.tracking_points.SubLevelTrackingPointSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SubLevelBlockTargetTest{
    @BeforeAll static void bootstrap(){ SableSplineConstraintTest.bootstrap(); }

    // Follow every selected face through native tracking transfers and graph reference reloads
    @Test void stableBindingsFollowNestedTransfersAndFaceRotations() throws Exception{
        ServerLevel level = level();
        var points = points(level);
        try(var storage = mockStatic(SubLevelTrackingPointSavedData.class);
            var collector = mockStatic(SubLevelBlockEntityCollector.class);
            var levels = mockStatic(SableLevelApi.class, CALLS_REAL_METHODS)){
            storage.when(() -> SubLevelTrackingPointSavedData.getOrLoad(level)).thenReturn(points);
            collector.when(() -> SubLevelBlockEntityCollector.isTargetLoaded(eq(level), any(), any())).thenReturn(true);
            for(Direction face : Direction.values()){
                BlockPos initial = new BlockPos(10, 70, 10);
                var original = new ControllerDirectTargetReference("test:block:" + face, "machine", "test", "Block",
                        null, initial).withFace(face);
                assertEquals(initial, SubLevelBlockTargetApi.resolve(level, original).blockPos());
                ServerSubLevel parent = mock(ServerSubLevel.class);
                ServerSubLevel child = mock(ServerSubLevel.class);
                UUID parentId = UUID.randomUUID(), childId = UUID.randomUUID();
                when(parent.getUniqueId()).thenReturn(parentId);
                when(child.getUniqueId()).thenReturn(childId);
                BlockPos from = initial;
                for(int step = 0; step < 4; step++){
                    BlockPos to = new BlockPos(1000 * (step + 1), 70, 1000);
                    ServerSubLevel destination = step == 0 ? parent : step == 1 ? child : null;
                    UUID destinationId = step == 3 ? null : step == 1 ? childId : parentId;
                    if(step == 2) levels.when(() -> SableLevelApi.containingId(level, to)).thenReturn(parentId);
                    Rotation rotation = step == 3 ? Rotation.CLOCKWISE_90 : Rotation.NONE;
                    var transform = new SubLevelAssemblyHelper.AssemblyTransform(from, to,
                            step == 3 ? 3 : 0, rotation, level);
                    SubLevelAssemblyHelper.moveTrackingPoints(level, BoundingBox3i.from(List.of(from)), destination, transform);
                    var saved = ControllerDirectTargetReference.fromTag(original.toTag());
                    var resolved = SubLevelBlockTargetApi.resolve(level, saved);
                    assertNotNull(resolved);
                    assertEquals(original.targetId(), resolved.targetId());
                    assertEquals(to, resolved.blockPos());
                    assertEquals(destinationId, resolved.subLevelId());
                    assertEquals(rotation.rotate(face), SubLevelBlockTargetApi.resolveFace(level, saved));
                    from = to;
                }
            }
        }
    }

    // Keep an unavailable binding instead of creating a new point at its old coordinates
    @Test void anUnloadedTargetResumesItsOriginalBinding() throws Exception{
        ServerLevel level = level();
        var points = points(level);
        BlockPos initial = new BlockPos(10, 70, 10), moved = initial.offset(1000, 0, 0);
        var target = new ControllerDirectTargetReference("test:unloaded", "machine", "test", "Block", null, initial);
        try(var storage = mockStatic(SubLevelTrackingPointSavedData.class);
            var collector = mockStatic(SubLevelBlockEntityCollector.class);
            var levels = mockStatic(SableLevelApi.class, CALLS_REAL_METHODS)){
            storage.when(() -> SubLevelTrackingPointSavedData.getOrLoad(level)).thenReturn(points);
            collector.when(() -> SubLevelBlockEntityCollector.isTargetLoaded(eq(level), any(), any())).thenReturn(true);
            assertNotNull(SubLevelBlockTargetApi.resolve(level, target));
            var transform = new SubLevelAssemblyHelper.AssemblyTransform(initial, moved, 0, Rotation.NONE, level);
            SubLevelAssemblyHelper.moveTrackingPoints(level, BoundingBox3i.from(List.of(initial)), null, transform);
            collector.when(() -> SubLevelBlockEntityCollector.isTargetLoaded(level, null, moved)).thenReturn(false);
            assertNull(SubLevelBlockTargetApi.resolve(level, target));
            collector.when(() -> SubLevelBlockEntityCollector.isTargetLoaded(level, null, moved)).thenReturn(true);
            assertEquals(moved, SubLevelBlockTargetApi.resolve(level, target).blockPos());
        }
    }

    // Retain optional faces through compatibility and location copies without changing legacy tags
    @Test void faceReferencesRoundTripWithoutChangingLegacyBindings(){
        var legacy = new ControllerDirectTargetReference("test:legacy", "machine", "test", "Block", null, BlockPos.ZERO);
        assertFalse(legacy.toTag().contains("Face"));
        assertEquals(legacy, ControllerDirectTargetReference.fromTag(legacy.toTag()));
        var selected = legacy.withFace(Direction.WEST).withCompatMode("face_redstone")
                .withLocation(UUID.randomUUID(), new BlockPos(1000, 70, 1000));
        assertEquals(Direction.WEST, selected.face());
        assertEquals(selected, ControllerDirectTargetReference.fromTag(selected.toTag()));
    }

    // Supply a loaded owning dimension without relying on world chunk checks
    private static ServerLevel level(){
        ServerLevel level = mock(ServerLevel.class);
        when(level.getServer()).thenReturn(mock(MinecraftServer.class));
        when(level.dimension()).thenReturn(Level.OVERWORLD);
        when(level.getBlockState(any())).thenReturn(Blocks.STONE.defaultBlockState());
        return level;
    }

    // Use Sable's real saved tracking-point collection
    private static SubLevelTrackingPointSavedData points(ServerLevel level) throws Exception{
        var ctor = SubLevelTrackingPointSavedData.class.getDeclaredConstructor(ServerLevel.class);
        ctor.setAccessible(true);
        return ctor.newInstance(level);
    }
}
