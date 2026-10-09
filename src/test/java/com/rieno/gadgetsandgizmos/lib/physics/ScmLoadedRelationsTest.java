package com.rieno.gadgetsandgizmos.lib.physics;

import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.scm.ScmSubLevelRelationRegistry;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScmLoadedRelationsTest{
    @BeforeAll
    static void bootstrap(){
        SableSplineConstraintTest.bootstrap();
    }

    @Test
    void emptyCompatibilityIsSkippedAndNewJointsAppearOnTheNextTick(){
        ServerLevel level = mock(ServerLevel.class);
        ServerSubLevel root = mock(ServerSubLevel.class);
        UUID rootId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        BlockEntity ordinary = mock(BlockEntity.class);
        BlockEntity joint = mock(BlockEntity.class);
        AtomicInteger calls = new AtomicInteger();
        when(root.getLevel()).thenReturn(level);
        when(root.getUniqueId()).thenReturn(rootId);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("test", "optional_loaded");
        var relation = new ScmSubLevelRelationRegistry.Relation(rootId, childId, "test:joint");
        ScmSubLevelRelationRegistry.register(id, 100, be -> be == joint, ctx -> {
            calls.incrementAndGet();
            assertEquals(1, ctx.blockEntities().size());
            return List.of(relation);
        });
        try(var collector = mockStatic(SubLevelBlockEntityCollector.class);
            var revisions = mockStatic(SableAssemblyTopologyInvalidation.class)){
            collector.when(() -> SubLevelBlockEntityCollector.getSubLevels(level)).thenReturn(List.of(root));
            collector.when(() -> SubLevelBlockEntityCollector.getBlockEntities(root)).thenReturn(List.of(ordinary));
            assertEquals(List.of(), ScmSubLevelRelationRegistry.loadedRelations(root));
            assertEquals(List.of(), ScmSubLevelRelationRegistry.loadedRelations(root));
            assertEquals(0, calls.get());
            collector.verify(() -> SubLevelBlockEntityCollector.getBlockEntities(root), times(1));
            when(level.getGameTime()).thenReturn(1L);
            collector.when(() -> SubLevelBlockEntityCollector.getBlockEntities(root)).thenReturn(List.of(ordinary, joint));
            assertEquals(List.of(relation), ScmSubLevelRelationRegistry.loadedRelations(root));
            assertEquals(List.of(relation), ScmSubLevelRelationRegistry.loadedRelations(root));
            assertEquals(1, calls.get());
            when(joint.isRemoved()).thenReturn(true);
            when(level.getGameTime()).thenReturn(2L);
            assertEquals(List.of(), ScmSubLevelRelationRegistry.loadedRelations(root));
        }finally{
            ScmSubLevelRelationRegistry.unregister(id);
            ScmSubLevelRelationRegistry.forgetLoadedRelations(level);
        }
    }

    @Test
    void topologyChangesAndLevelUnloadingRefreshWithinTheSameTick(){
        ServerLevel level = mock(ServerLevel.class);
        ServerSubLevel root = mock(ServerSubLevel.class);
        BlockEntity joint = mock(BlockEntity.class);
        UUID rootId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        when(root.getLevel()).thenReturn(level);
        when(root.getUniqueId()).thenReturn(rootId);
        AtomicLong revision = new AtomicLong();
        AtomicInteger calls = new AtomicInteger();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("test", "loaded_revisions");
        var relation = new ScmSubLevelRelationRegistry.Relation(rootId, childId, "test:joint");
        ScmSubLevelRelationRegistry.register(id, 100, ctx -> {
            calls.incrementAndGet();
            return List.of(relation);
        });
        try(var collector = mockStatic(SubLevelBlockEntityCollector.class);
            var revisions = mockStatic(SableAssemblyTopologyInvalidation.class)){
            revisions.when(() -> SableAssemblyTopologyInvalidation.revision(level)).thenAnswer(call -> revision.get());
            collector.when(() -> SubLevelBlockEntityCollector.getSubLevels(level)).thenReturn(List.of(root));
            collector.when(() -> SubLevelBlockEntityCollector.getBlockEntities(root)).thenReturn(List.of(joint));
            assertEquals(List.of(relation), ScmSubLevelRelationRegistry.loadedRelations(root));
            revision.incrementAndGet();
            assertEquals(List.of(relation), ScmSubLevelRelationRegistry.loadedRelations(root));
            assertEquals(2, calls.get());
            ScmSubLevelRelationRegistry.forgetLoadedRelations(level);
            assertEquals(List.of(relation), ScmSubLevelRelationRegistry.loadedRelations(root));
            assertEquals(3, calls.get());
            ScmSubLevelRelationRegistry.unregister(id);
            assertEquals(List.of(), ScmSubLevelRelationRegistry.loadedRelations(root));
            assertEquals(3, calls.get());
        }finally{
            ScmSubLevelRelationRegistry.unregister(id);
            ScmSubLevelRelationRegistry.forgetLoadedRelations(level);
        }
    }
}
