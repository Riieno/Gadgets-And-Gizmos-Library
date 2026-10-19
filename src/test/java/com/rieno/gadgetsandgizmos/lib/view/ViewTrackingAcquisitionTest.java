package com.rieno.gadgetsandgizmos.lib.view;

import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyHullApi;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ViewTrackingAcquisitionTest{
    private static final UUID PLAYER_ID = UUID.fromString("891ac7d6-5278-4c6f-8e78-58e03d6d7950");
    private static final AABB NEAR = new AABB(2.7, -.9, -10.3, 3.3, .9, -9.7);

    @BeforeAll static void prepareRegistries(){
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        net.neoforged.fml.ModList.of(List.of(), List.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void offCentrePlayersAcquireFromAConfirmedConeProbe(){
        var hit = new ViewRaycast.Result("entity", new Vec3(3, 0, -10), 10.44,
                BlockPos.ZERO, null, null, "minecraft:player", PLAYER_ID.toString(), "");
        Scan res = scan(NEAR, true, hit);
        assertEquals(1, res.targets().size());
        assertEquals(PLAYER_ID, res.targets().getFirst().key().playerId());
        assertTrue(res.probes() > 0);
    }

    @Test void nearbyBoundsDoNotAcquireThroughBlockingGeometry(){
        var blocked = new ViewRaycast.Result("block", new Vec3(1.5, 0, -5), 5.22,
                BlockPos.ZERO, null, null, "", "", "north");
        Scan res = scan(NEAR, true, blocked);
        assertTrue(res.targets().isEmpty());
        assertTrue(res.probes() > 0);
    }

    @Test void rejectedFiltersDoNotEvenProbeTheCandidate(){
        Scan res = scan(NEAR, false, ViewRaycast.Result.miss(Vec3.ZERO, 0));
        assertTrue(res.targets().isEmpty());
        assertEquals(0, res.probes());
    }

    @Test void candidatesOutsideTheConeOrRangeAreNotProbed(){
        for(AABB bounds : List.of(new AABB(20, -1, -10, 21, 1, -9), new AABB(-1, -1, -80, 1, 1, -70))){
            Scan res = scan(bounds, true, ViewRaycast.Result.miss(Vec3.ZERO, 0));
            assertTrue(res.targets().isEmpty());
            assertEquals(0, res.probes());
        }
    }

    @Test void offCentreBodiesRequireAnAcceptedBlockHitAndExcludeTheCameraBody(){
        Scan accepted = scanBody(true, false);
        assertEquals(1, accepted.targets().size());
        assertNotNull(accepted.targets().getFirst().key().bodyId());
        assertEquals(BlockPos.ZERO, accepted.targets().getFirst().key().blockPos());
        assertTrue(scanBody(false, false).targets().isEmpty());
        Scan excluded = scanBody(true, true);
        assertTrue(excluded.targets().isEmpty());
        assertEquals(0, excluded.probes());
    }

    private static Scan scanBody(boolean accepted, boolean excluded){
        ServerLevel root = mock(ServerLevel.class);
        SubLevel body = mock(SubLevel.class);
        ServerChunkCache chunks = mock(ServerChunkCache.class);
        LevelChunk chunk = mock(LevelChunk.class);
        ViewRaycast.Filter filter = mock(ViewRaycast.Filter.class);
        UUID bodyId = UUID.fromString("b96cf6b2-dab1-41b3-a53d-a40b9aec20b3");
        AABB bounds = new AABB(3, -1, -12, 8, 1, -8);
        when(root.players()).thenReturn(List.of());
        when(root.getChunkSource()).thenReturn(chunks);
        when(chunks.getChunkNow(0, 0)).thenReturn(chunk);
        when(chunk.getBlockState(BlockPos.ZERO)).thenReturn(Blocks.STONE.defaultBlockState());
        when(body.getUniqueId()).thenReturn(bodyId);
        when(body.getLevel()).thenReturn(root);
        when(filter.accepts(Blocks.STONE.defaultBlockState(), bodyId)).thenReturn(accepted);
        AtomicInteger probes = new AtomicInteger();
        ViewReference self = excluded ? new ViewReference(net.minecraft.resources.ResourceLocation
                .fromNamespaceAndPath("test", "dimension"), bodyId, BlockPos.ZERO) : null;
        try(var levels = mockStatic(SableLevelApi.class);
            var transforms = mockStatic(SableTransformApi.class);
            var hulls = mockStatic(SableAssemblyHullApi.class);
            var rays = mockStatic(ViewRaycast.class)){
            levels.when(() -> SableLevelApi.serverLevel(any(Level.class))).thenReturn(root);
            levels.when(() -> SableLevelApi.subLevel(root, bodyId)).thenReturn(body);
            transforms.when(() -> SableTransformApi.intersecting(any(Level.class), any(AABB.class))).thenReturn(List.of(body));
            hulls.when(() -> SableAssemblyHullApi.sample(eq(body), anyCollection()))
                    .thenReturn(new SableAssemblyHullApi.Snapshot(List.of(bounds), Map.of()));
            rays.when(() -> ViewRaycast.traceToward(eq(root), any(Vec3.class), any(Vec3.class),
                    anyDouble(), eq(filter), eq(self))).thenAnswer(call -> {
                probes.incrementAndGet();
                return new ViewRaycast.Result("block", new Vec3(3, 0, -10), 10.44,
                        BlockPos.ZERO, Blocks.STONE.defaultBlockState(), bodyId, "", "", "north");
            });
            var cone = new ViewAcquisitionCone(Vec3.ZERO, new Vec3(0, 0, -1), 64, 1.5, 15);
            return new Scan(ViewTrackingTargets.coneHits(root, cone, filter, self), probes.get());
        }
    }

    @Test void passiveAndHostileMobsAcquireUsingTheSameConfirmedGeometry(){
        for(var type : List.of(net.minecraft.world.entity.EntityType.COW, net.minecraft.world.entity.EntityType.ZOMBIE)){
            var hit = new ViewRaycast.Result("entity", new Vec3(3, 0, -10), 10.44,
                    BlockPos.ZERO, null, null, net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(),
                    PLAYER_ID.toString(), "");
            Scan result = scan(NEAR, true, hit, true);
            assertEquals(1, result.targets().size());
            assertEquals(PLAYER_ID, result.targets().getFirst().key().playerId());
            assertTrue(result.probes() > 0);
        }
    }

    private static Scan scan(AABB bounds, boolean accepted, ViewRaycast.Result hit){
        return scan(bounds, accepted, hit, false);
    }

    private static Scan scan(AABB bounds, boolean accepted, ViewRaycast.Result hit, boolean mob){
        ServerLevel root = mock(ServerLevel.class);
        Entity player = mob ? mock(net.minecraft.world.entity.Mob.class) : mock(ServerPlayer.class);
        ViewRaycast.Filter filter = mock(ViewRaycast.Filter.class);
        when(root.players()).thenReturn(mob ? List.of() : List.of((ServerPlayer)player));
        when(root.getEntity(PLAYER_ID)).thenReturn(player);
        when(root.getEntities((Entity)isNull(), any(AABB.class), any(java.util.function.Predicate.class)))
                .thenReturn(mob ? List.of(player) : List.of());
        when(player.getUUID()).thenReturn(PLAYER_ID);
        when(player.isAlive()).thenReturn(true);
        when(player.isPickable()).thenReturn(true);
        when(player.getBoundingBox()).thenReturn(bounds);
        when(filter.accepts(any(Entity.class), isNull())).thenReturn(accepted);
        AtomicInteger probes = new AtomicInteger();
        try(var levels = mockStatic(SableLevelApi.class);
            var transforms = mockStatic(SableTransformApi.class);
            var rays = mockStatic(ViewRaycast.class)){
            levels.when(() -> SableLevelApi.serverLevel(any(Level.class))).thenReturn(root);
            transforms.when(() -> SableTransformApi.intersecting(any(Level.class), any(AABB.class))).thenReturn(List.of());
            transforms.when(() -> SableTransformApi.toWorldPosition(
                    (dev.ryanhcode.sable.sublevel.SubLevel)isNull(), any(Vec3.class)))
                    .thenAnswer(call -> call.getArgument(1));
            rays.when(() -> ViewRaycast.traceToward(eq(root), any(Vec3.class), any(Vec3.class),
                    anyDouble(), eq(filter), isNull())).thenAnswer(call -> {
                probes.incrementAndGet();
                return hit;
            });
            var cone = new ViewAcquisitionCone(Vec3.ZERO, new Vec3(0, 0, -1), 64, 1.5, 15);
            return new Scan(ViewTrackingTargets.coneHits(root, cone, filter, null), probes.get());
        }
    }

    private record Scan(List<ViewTargetLock.Target<ViewTrackingTargets.Reference>> targets, int probes){}
}
