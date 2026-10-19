package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;

// Check initial-contact clearance against loaded Minecraft terrain and a native Sable body
@GameTestHolder("gadgetsngizmos")
@PrefixGameTestTemplate(false)
public final class CollisionContactGameTests{
    // Keep test fixtures local to each game test
    private CollisionContactGameTests(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    ========================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Permit a padded hull to move beside terrain while rejecting motion into its physical face
    @GameTest(template = "empty_container")
    public static void terrainContactAllowsSafeDeparture(GameTestHelper ctx){
        BlockPos block = new BlockPos(3, 3, 2);
        ctx.setBlock(block, Blocks.STONE);
        BlockPos wall = ctx.absolutePos(block);
        Vec3 anchor = new Vec3(wall.getX() - 0.55D, wall.getY() + 0.5D, wall.getZ() + 0.5D);
        AABB padded = new AABB(anchor.x - 0.5D, anchor.y - 0.5D, anchor.z - 0.5D,
                anchor.x + 0.5D, anchor.y + 0.5D, anchor.z + 0.5D).inflate(0.125D);
        verify(ctx, anchor, padded, true);
        ctx.succeed();
    }

    // Apply the same rule after transforming a sweep into a neighbouring Sable plot
    @GameTest(template = "empty_container")
    public static void neighbouringShipAllowsSafeDeparture(GameTestHelper ctx){
        var level = ctx.getLevel();
        var container = SubLevelContainer.getContainer(level);
        var body = (ServerSubLevel)container.allocateNewSubLevel(new Pose3d());
        try{
            var plot = body.getPlot();
            BlockPos center = plot.getCenterBlock();
            plot.newEmptyChunk(plot.getCenterChunk());
            BlockPos world = ctx.absolutePos(new BlockPos(3, 3, 2));
            plot.getLoadedChunks().iterator().next().getChunk().setBlockState(center, Blocks.STONE.defaultBlockState(), false);
            container.physicsSystem().getPipeline().onStatsChanged(body);
            Pose3d pose = new Pose3d();
            pose.position().set(world.getX(), world.getY(), world.getZ());
            pose.rotationPoint().set(center.getX(), center.getY(), center.getZ());
            body.logicalPose().set(pose);
            body.updateLastPose();
            body.updateBoundingBox();
            var bounds = body.boundingBox();
            ctx.assertTrue(Math.abs(bounds.minX() - world.getX()) < 1.0E-6D
                    && Math.abs(bounds.maxX() - world.getX() - 1) < 1.0E-6D,
                    "Native fixture bounds differ from its block: x=" + bounds.minX() + ".." + bounds.maxX() + ", world=" + world
                    + ", plot=" + plot.getBoundingBox() + ", center=" + center);
            Vec3 anchor = new Vec3(world.getX() - 0.55D, world.getY() + 0.5D, world.getZ() + 0.5D);
            AABB padded = new AABB(anchor.x - 0.5D, anchor.y - 0.5D, anchor.z - 0.5D,
                    anchor.x + 0.5D, anchor.y + 0.5D, anchor.z + 0.5D).inflate(0.125D);
            verify(ctx, anchor, padded, false);
            pose.orientation().rotateY(Math.toRadians(30));
            body.logicalPose().set(pose);
            body.updateBoundingBox();
            bounds = body.boundingBox();
            anchor = new Vec3(bounds.minX() - 0.55D, world.getY() + 0.5D,
                    (bounds.minZ() + bounds.maxZ()) * 0.5D);
            padded = new AABB(anchor.x - 0.5D, anchor.y - 0.5D, anchor.z - 0.5D,
                    anchor.x + 0.5D, anchor.y + 0.5D, anchor.z + 0.5D).inflate(0.125D);
            verify(ctx, anchor, padded, false);
            ctx.succeed();
        }finally{
            if(container.getSubLevel(body.getUniqueId()) == body) SableSubLevelLifecycleApi.remove(level, List.of(body));
        }
    }

    // Compare live probes, synchronous sweeps and retained incremental work on the same scene
    private static void verify(GameTestHelper ctx, Vec3 anchor, AABB padded, boolean terrain){
        for(Vec3 dir : List.of(new Vec3(-1, 0, 0), new Vec3(0, 0, 1), new Vec3(1, 0, 0))){
            boolean blocked = dir.x > 0;
            double live = SubLevelParticleOcclusion.findAirOnlyBoundsBlockingDistance(
                    ctx.getLevel(), dir, 0.5D, List.of(padded), terrain, Set.of(), 16, null, 0.125D);
            double exact = SubLevelParticleOcclusion.findSweptBoundsBlockingDistance(
                    ctx.getLevel(), null, anchor, dir, 0.5D, List.of(padded), terrain, Set.of(), true, 0.125D);
            var scan = SubLevelParticleOcclusion.beginSweptBoundsBlockingDistanceScan(
                    ctx.getLevel(), null, anchor, dir, 0.5D, List.of(padded), terrain, Set.of(), true, 0.125D);
            for(int idx = 0; idx < 1000 && !scan.isComplete(); idx++) scan.advance(16, 0L);
            ctx.assertTrue(scan.isComplete(), "Incremental contact sweep failed to finish");
            ctx.assertTrue(blocked ? live == 0 && exact == 0 && scan.result() == 0
                    : live >= 0.5D && exact >= 0.5D && scan.result() >= 0.5D,
                    "Contact clearance disagrees for " + dir + ": " + live + ", " + exact + ", " + scan.result());
        }
    }
}
