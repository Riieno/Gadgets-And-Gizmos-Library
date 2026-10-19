package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.physics.archive.SubLevelSchematicCapture;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// Exercise capture and avoidance against native plots and real attachment handles
@GameTestHolder("gadgetsngizmos")
@PrefixGameTestTemplate(false)
public final class SubLevelInteractionGameTests{
    private SubLevelInteractionGameTests(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    ========================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @GameTest(template = "empty_container")
    public static void capturePromotesPendingSourceConfiguration(GameTestHelper ctx){
        ServerSubLevel body = body(ctx, new Vec3(3, 4, 3), true);
        try{
            BlockPos pos = body.getPlot().getCenterBlock();
            ChestBlockEntity chest = (ChestBlockEntity)SubLevelBlockEntityCollector.getOrCreateBlockEntity(body, pos);
            ctx.assertTrue(chest != null, "Source chest is missing");
            chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
            var saved = chest.saveWithFullMetadata(ctx.getLevel().registryAccess());
            var chunk = body.getPlot().getLoadedChunks().iterator().next().getChunk();
            chunk.removeBlockEntity(pos);
            chunk.setBlockEntityNbt(saved);
            var schematic = SubLevelSchematicCapture.capture(ctx.getLevel(), body.getUniqueId(), 8, 64, source -> {});
            ctx.assertTrue(schematic.blockCount() == 1, "Pending block entity prevented capture");
            var restored = (ChestBlockEntity)SubLevelBlockEntityCollector.getBlockEntity(body, pos);
            ctx.assertTrue(restored != null && restored.getItem(0).is(Items.DIAMOND) && restored.getItem(0).getCount() == 3,
                    "Capture lost the source configuration");
            ctx.assertTrue(!body.isRemoved(), "Saving removed the live source body");
            ctx.succeed();
        }finally{ remove(ctx, List.of(body)); }
    }

    @GameTest(template = "empty_container")
    public static void physicalAttachmentsStayInsideCollisionOwnership(GameTestHelper ctx) throws Exception{
        List<ServerSubLevel> bodies = List.of(body(ctx, new Vec3(2, 4, 2), false),
                body(ctx, new Vec3(3, 4, 2), false), body(ctx, new Vec3(4, 4, 2), false));
        List<PhysicsConstraintHandle> handles = new ArrayList<>();
        try{
            for(int idx = 1; idx < bodies.size(); idx++){
                ServerSubLevel first = bodies.get(idx - 1), second = bodies.get(idx);
                handles.add((PhysicsConstraintHandle)SableConstraintApi.addConstraint(
                        SubLevelContainer.getContainer(ctx.getLevel()).physicsSystem().getPipeline(), first, second,
                        SableConstraintApi.fixedConfiguration(vector(first.getPlot().getCenterBlock()),
                                vector(second.getPlot().getCenterBlock()), new Quaterniond())));
            }
            var ids = SablePhysicalConnectionApi.connectedBodyIds(ctx.getLevel(), Set.of(bodies.getFirst().getUniqueId()));
            ctx.assertTrue(ids.size() == 3, "Native constraint attachments were omitted");
            var bounds = SableAssemblyHullApi.sample(bodies.getFirst(), bodies).worldBounds();
            var hazard = SubLevelParticleOcclusion.findSubLevelProximityHazard(ctx.getLevel(), bounds, Vec3.ZERO, ids, 1, 1);
            ctx.assertTrue(!hazard.available(), "Connected bodies were treated as approaching obstacles");
            ctx.assertTrue(SubLevelParticleOcclusion.findAirOnlyBoundsBlockingDistance(ctx.getLevel(),
                    new Vec3(1, 0, 0), 2, bounds, false, ids, 16, null) == 2,
                    "Connected bodies blocked their own compound hull");
            long revision = SablePhysicalConnectionApi.revision(ctx.getLevel());
            handles.getLast().remove();
            ctx.assertTrue(SablePhysicalConnectionApi.connectedBodyIds(ctx.getLevel(),
                    Set.of(bodies.getFirst().getUniqueId())).size() == 2, "Detached body stayed excluded");
            ctx.assertTrue(SablePhysicalConnectionApi.revision(ctx.getLevel()) > revision, "Detach did not invalidate collision membership");
            ctx.succeed();
        }finally{
            for(var handle : handles) if(handle != null && handle.isValid()) handle.remove();
            remove(ctx, bodies);
        }
    }

    @GameTest(template = "empty_container")
    public static void approachingBodyUsesItsCurrentPose(GameTestHelper ctx){
        ServerSubLevel intruder = body(ctx, new Vec3(20, 4, 3), false);
        try{
            Vec3 origin = Vec3.atLowerCornerOf(ctx.absolutePos(new BlockPos(3, 4, 3)));
            AABB hull = new AABB(origin.x - 1, origin.y, origin.z, origin.x, origin.y + 1, origin.z + 1);
            var cache = new SubLevelParticleOcclusion.ProbeCache();
            ctx.assertTrue(!SubLevelParticleOcclusion.findSubLevelProximityHazard(ctx.getLevel(), List.of(hull),
                    Vec3.ZERO, Set.of(), 1, 1, cache).available(), "Distant stationary body caused an escape");
            Pose3d pose = new Pose3d().set(intruder.logicalPose());
            pose.position().set(origin.x + 2, origin.y, origin.z);
            intruder.logicalPose().set(pose);
            intruder.latestLinearVelocity.set(-3, 0, 0);
            var hazard = SubLevelParticleOcclusion.findSubLevelProximityHazard(ctx.getLevel(), List.of(hull), Vec3.ZERO, Set.of(), 1, 1, cache);
            ctx.assertTrue(hazard.available() && hazard.direction().x > 0, "A moving side intruder was invisible before bounds refresh");
            pose.position().set(origin.x - 0.25D, origin.y, origin.z);
            intruder.logicalPose().set(pose);
            List<AABB> padded = List.of(hull.inflate(0.125D));
            double departure = SubLevelParticleOcclusion.findAirOnlyDepartureBlockingDistance(ctx.getLevel(),
                    new Vec3(-1, 0, 0), 2, padded, false, Set.of(), 16, null, 0.125D);
            double inward = SubLevelParticleOcclusion.findAirOnlyDepartureBlockingDistance(ctx.getLevel(),
                    new Vec3(1, 0, 0), 2, padded, false, Set.of(), 16, null, 0.125D);
            ctx.assertTrue(departure == 2 && inward == 0, "Forced overlap could not be left safely: " + departure + ", " + inward);
            ctx.succeed();
        }finally{ remove(ctx, List.of(intruder)); }
    }

    @GameTest(template = "empty_container")
    public static void preparedProbeCacheSeesNewBodiesImmediately(GameTestHelper ctx){
        Vec3 origin = Vec3.atLowerCornerOf(ctx.absolutePos(new BlockPos(3, 4, 3)));
        List<AABB> hull = List.of(new AABB(origin.x - 1, origin.y, origin.z, origin.x, origin.y + 1, origin.z + 1));
        Set<java.util.UUID> excluded = SableLevelApi.subLevels(ctx.getLevel()).stream()
                .map(body -> body.getUniqueId()).collect(java.util.stream.Collectors.toSet());
        var cache = new SubLevelParticleOcclusion.ProbeCache();
        ctx.assertTrue(!SubLevelParticleOcclusion.findSubLevelProximityHazard(ctx.getLevel(), hull, Vec3.ZERO,
                excluded, 1, 1, cache).available(), "Empty initial scene was blocked");
        ServerSubLevel intruder = body(ctx, new Vec3(3.5D, 4, 3), false);
        try{
            ctx.assertTrue(SubLevelParticleOcclusion.findSubLevelProximityHazard(ctx.getLevel(), hull, Vec3.ZERO,
                    excluded, 1, 1, cache).available(), "A cached scene hid a newly loaded body");
            ctx.succeed();
        }finally{ remove(ctx, List.of(intruder)); }
    }

    // Place a native body above the template so terrain cannot affect the fixture
    private static ServerSubLevel body(GameTestHelper ctx, Vec3 relative, boolean chest){
        var container = SubLevelContainer.getContainer(ctx.getLevel());
        ServerSubLevel body = (ServerSubLevel)container.allocateNewSubLevel(new Pose3d());
        var plot = body.getPlot();
        plot.newEmptyChunk(plot.getCenterChunk());
        plot.getLoadedChunks().iterator().next().getChunk().setBlockState(plot.getCenterBlock(),
                (chest ? Blocks.CHEST : Blocks.STONE).defaultBlockState(), false);
        container.physicsSystem().getPipeline().onStatsChanged(body);
        Pose3d pose = new Pose3d();
        BlockPos center = plot.getCenterBlock();
        Vec3 world = ctx.absoluteVec(relative);
        pose.position().set(world.x, world.y, world.z);
        pose.rotationPoint().set(center.getX(), center.getY(), center.getZ());
        body.logicalPose().set(pose);
        body.updateLastPose();
        body.updateBoundingBox();
        return body;
    }

    private static Vector3d vector(BlockPos pos){ return new Vector3d(pos.getX(), pos.getY(), pos.getZ()); }

    private static void remove(GameTestHelper ctx, List<ServerSubLevel> bodies){
        var container = SubLevelContainer.getContainer(ctx.getLevel());
        List<ServerSubLevel> live = bodies.stream().filter(body -> container.getSubLevel(body.getUniqueId()) == body).toList();
        if(!live.isEmpty()) SableSubLevelLifecycleApi.remove(ctx.getLevel(), live);
    }
}
