package com.rieno.gadgetsandgizmos.lib.view;

import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.joml.Quaterniond;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Verify loaded geometry, filter penetration and transformed hit coordinates
class ViewRaycastTest{
    // Initialize vanilla shapes before reading block states
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
    // Apply the same filter syntax to scalar text and typed graph lists
    @Test
    void filterTextAndGraphListsShareTheLibraryParser(){
        var first = ViewRaycast.Filter.fromValue(com.rieno.gadgetsandgizmos.lib.graph.GraphValue.string(
                "minecraft:stone, #minecraft:logs; minecraft:pig"), true);
        var second = ViewRaycast.Filter.fromValue(com.rieno.gadgetsandgizmos.lib.graph.GraphValue.list(List.of(
                com.rieno.gadgetsandgizmos.lib.graph.GraphValue.string("minecraft:stone"),
                com.rieno.gadgetsandgizmos.lib.graph.GraphValue.string("#minecraft:logs minecraft:pig minecraft:stone"))), true);
        assertEquals(Set.of("minecraft:stone", "#minecraft:logs", "minecraft:pig"), first.entries());
        assertEquals(first, second);
    }
    // Combine categories without confusing root blocks, body geometry or mob types
    @Test
    void categoryFiltersSupportIndependentMultipleSelections(){
        UUID body = UUID.randomUUID();
        var filter = ViewRaycast.Filter.fromValue(com.rieno.gadgetsandgizmos.lib.graph.GraphValue.string(
                "Sub-levels, Passive Mobs, Players"), true);
        assertEquals(Set.of("sub_levels", "passive_mobs", "players"), filter.entries());
        assertFalse(filter.accepts(Blocks.STONE.defaultBlockState(), null));
        assertTrue(filter.accepts(Blocks.STONE.defaultBlockState(), body));
        var passive = mock(net.minecraft.world.entity.Mob.class);
        doReturn(EntityType.PIG).when(passive).getType();
        var hostile = mock(net.minecraft.world.entity.Mob.class);
        doReturn(EntityType.ZOMBIE).when(hostile).getType();
        var player = mock(net.minecraft.world.entity.player.Player.class);
        doReturn(EntityType.PLAYER).when(player).getType();
        for(Entity entity : List.of(passive, hostile, player)) when(entity.getUUID()).thenReturn(UUID.randomUUID());
        assertTrue(filter.accepts(passive, null));
        assertFalse(filter.accepts(hostile, null));
        assertTrue(filter.accepts(player, null));
        var blacklist = new ViewRaycast.Filter(Set.of("hostile_mobs", "blocks"), false);
        assertFalse(blacklist.accepts(hostile, null));
        assertFalse(blacklist.accepts(Blocks.STONE.defaultBlockState(), null));
        assertTrue(blacklist.accepts(Blocks.STONE.defaultBlockState(), body));
        assertTrue(blacklist.accepts(player, null));
        assertFalse(new ViewRaycast.Filter(Set.of(), true).accepts(player, null));
        assertTrue(new ViewRaycast.Filter(Set.of(), false).accepts(player, null));
    }
    // Ignore excluded blocks and report the next accepted shape
    @Test
    void blacklistAndAllowlistTraceThroughIgnoredBlocks(){
        ServerLevel level = level();
        terrain(level);
        try(var transforms = mockStatic(SableTransformApi.class, CALLS_REAL_METHODS);
            var levels = mockStatic(SableLevelApi.class)){
            transforms.when(() -> SableTransformApi.intersecting(eq(level), any())).thenReturn(List.of());
            var pose = new ViewPose(new Vec3(0.5D, 0.5D, 0.5D), new Quaterniond(), 70);
            var blacklist = new ViewRaycast.Filter(Set.of("minecraft:dirt"), false);
            var allowlist = new ViewRaycast.Filter(Set.of("minecraft:stone"), true);
            var first = ViewRaycast.trace(level, pose, 8, blacklist, null, 0, 1);
            var second = ViewRaycast.trace(level, pose, 8, allowlist, null, 0, 1);
            assertTrue(first.hit());
            assertEquals(new BlockPos(0, 0, -4), first.localBlockPos());
            assertEquals(new Vec3(0.5D, 0.5D, -3), first.position());
            assertEquals(3.5D, first.distance(), 1.0E-9D);
            assertEquals(first, second);
            var details = (Map<?, ?>) first.details().value();
            assertEquals("minecraft:stone", ((com.rieno.gadgetsandgizmos.lib.graph.GraphValue) details.get("block")).value());
            verify(level, never()).getBlockState(any());
        }
    }
    // Treat absent chunks as unavailable without requesting a loading read
    @Test
    void missingChunksRemainAnExplicitMiss(){
        ServerLevel level = level();
        try(var transforms = mockStatic(SableTransformApi.class, CALLS_REAL_METHODS);
            var levels = mockStatic(SableLevelApi.class)){
            transforms.when(() -> SableTransformApi.intersecting(eq(level), any())).thenReturn(List.of());
            var pose = new ViewPose(new Vec3(0.5D, 0.5D, 0.5D), new Quaterniond(), 70);
            var hit = ViewRaycast.trace(level, pose, 8, new ViewRaycast.Filter(Set.of(), false), null, 0, 1);
            assertFalse(hit.hit());
            assertEquals(new Vec3(0.5D, 0.5D, -7.5D), hit.position());
            verify(level, never()).getBlockState(any());
            verify(level.getChunkSource(), never()).getChunk(anyInt(), anyInt(), any(), anyBoolean());
        }
    }
    // Return world coordinates while retaining the moving body's local block position
    @Test
    void rotatedSubLevelKeepsBothHitFrames(){
        ServerLevel root = level();
        ServerLevel plot = level();
        terrain(plot);
        SubLevel body = mock(SubLevel.class);
        Pose3d bodyPose = new Pose3d();
        bodyPose.position().set(20, 30, 40);
        bodyPose.orientation().rotationY(Math.PI * 0.5D);
        UUID id = UUID.randomUUID();
        when(body.getUniqueId()).thenReturn(id);
        when(body.getLevel()).thenReturn(plot);
        when(body.logicalPose()).thenReturn(bodyPose);
        try(var transforms = mockStatic(SableTransformApi.class, CALLS_REAL_METHODS);
            var levels = mockStatic(SableLevelApi.class)){
            transforms.when(() -> SableTransformApi.intersecting(eq(root), any())).thenReturn(List.of(body));
            Vec3 start = bodyPose.transformPosition(new Vec3(0.5D, 0.5D, 0.5D));
            var pose = new ViewPose(start, bodyPose.orientation(), 70);
            var hit = ViewRaycast.trace(root, pose, 8,
                    new ViewRaycast.Filter(Set.of(id.toString()), true), null, 0, 1);
            assertTrue(hit.hit());
            assertEquals(id, hit.subLevelId());
            assertEquals(new BlockPos(0, 0, -2), hit.localBlockPos());
            assertTrue(bodyPose.transformPosition(new Vec3(0.5D, 0.5D, -1)).distanceTo(hit.position()) < 1.0E-8D);
            assertEquals(1.5D, hit.distance(), 1.0E-8D);
            verify(plot, never()).getBlockState(any());
        }
    }
    // Include body ownership for world-space entities and intersections starting inside them
    @Test
    void trackedEntitiesKeepTheirWorldGeometryAndBodyDetails(){
        ServerLevel level = level();
        terrain(level);
        Entity entity = mock(Entity.class);
        when(entity.isAlive()).thenReturn(true);
        when(entity.isPickable()).thenReturn(true);
        doReturn(EntityType.PIG).when(entity).getType();
        when(entity.getUUID()).thenReturn(UUID.randomUUID());
        when(entity.getBoundingBox()).thenReturn(new AABB(0.2D, 0.2D, -1.5D, 0.8D, 0.8D, -0.5D));
        when(level.getEntities(nullable(Entity.class), any(), any())).thenReturn(List.of(entity));
        SubLevel body = mock(SubLevel.class);
        UUID id = UUID.randomUUID();
        Pose3d bodyPose = new Pose3d();
        bodyPose.position().set(10, 0, 0);
        when(body.getUniqueId()).thenReturn(id);
        when(body.logicalPose()).thenReturn(bodyPose);
        try(var transforms = mockStatic(SableTransformApi.class, CALLS_REAL_METHODS);
            var levels = mockStatic(SableLevelApi.class)){
            transforms.when(() -> SableTransformApi.intersecting(eq(level), any())).thenReturn(List.of());
            levels.when(() -> SableLevelApi.tracking(entity)).thenReturn(body);
            var pose = new ViewPose(new Vec3(0.5D, 0.5D, 0.5D), new Quaterniond(), 70);
            var filter = new ViewRaycast.Filter(Set.of(id.toString()), true);
            var hit = ViewRaycast.trace(level, pose, 8, filter, null, 0, 1);
            assertEquals("entity", hit.kind());
            assertEquals(id, hit.subLevelId());
            assertEquals(1, hit.distance(), 1.0E-9D);
            assertEquals(new Vec3(0.5D, 0.5D, -0.5D), hit.position());
            assertEquals(new BlockPos(-10, 0, -1), hit.localBlockPos());
            when(entity.getBoundingBox()).thenReturn(new AABB(0, 0, 0, 1, 1, 1));
            var inside = ViewRaycast.trace(level, pose, 8, filter, null, 0, 1);
            assertEquals("entity", inside.kind());
            assertEquals(0, inside.distance());
        }
    }
    // Prevent a custom block shape from requesting unavailable neighboring chunks
    @Test
    void neighboringShapeReadsUseTheLoadedTerrainView(){
        ServerLevel level = level();
        LevelChunk chunk = mock(LevelChunk.class);
        when(level.getChunkSource().getChunkNow(0, -1)).thenReturn(chunk);
        BlockState state = mock(BlockState.class);
        when(state.getBlock()).thenReturn(Blocks.STONE);
        when(chunk.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
        when(chunk.getBlockState(eq(new BlockPos(0, 0, -2)))).thenReturn(state);
        when(state.getShape(any(BlockGetter.class), any(BlockPos.class))).thenAnswer(call -> {
            BlockGetter getter = call.getArgument(0);
            assertTrue(getter.getBlockState(new BlockPos(1000, 0, 1000)).isAir());
            return Shapes.block();
        });
        try(var transforms = mockStatic(SableTransformApi.class, CALLS_REAL_METHODS);
            var levels = mockStatic(SableLevelApi.class)){
            transforms.when(() -> SableTransformApi.intersecting(eq(level), any())).thenReturn(List.of());
            var pose = new ViewPose(new Vec3(0.5D, 0.5D, 0.5D), new Quaterniond(), 70);
            assertTrue(ViewRaycast.trace(level, pose, 8,
                    new ViewRaycast.Filter(Set.of(), false), null, 0, 1).hit());
            verify(level, never()).getBlockState(any());
            verify(level.getChunkSource(), times(1)).getChunkNow(62, 62);
            verify(level.getChunkSource(), never()).getChunk(anyInt(), anyInt(), any(), anyBoolean());
        }
    }
    // Provide a loaded-only level without Sable or entity implementation dependencies
    private static ServerLevel level(){
        ServerLevel level = mock(ServerLevel.class);
        when(level.dimension()).thenReturn(Level.OVERWORLD);
        when(level.getChunkSource()).thenReturn(mock(ServerChunkCache.class));
        when(level.getMinBuildHeight()).thenReturn(-64);
        when(level.getMaxBuildHeight()).thenReturn(320);
        when(level.getEntities(nullable(net.minecraft.world.entity.Entity.class), any(), any())).thenReturn(List.of());
        return level;
    }
    // Place two complete shapes on the negative Z lens ray
    private static void terrain(ServerLevel level){
        LevelChunk chunk = mock(LevelChunk.class);
        when(level.getChunkSource().getChunkNow(anyInt(), anyInt())).thenReturn(chunk);
        when(chunk.getBlockState(any())).thenAnswer(call -> {
            BlockPos pos = call.getArgument(0);
            if(pos.equals(new BlockPos(0, 0, -2))) return Blocks.DIRT.defaultBlockState();
            if(pos.equals(new BlockPos(0, 0, -4))) return Blocks.STONE.defaultBlockState();
            return Blocks.AIR.defaultBlockState();
        });
    }
}
