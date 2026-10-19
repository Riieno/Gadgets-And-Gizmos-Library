package com.rieno.gadgetsandgizmos.lib.physics;

import com.rieno.gadgetsandgizmos.lib.compat.simulated.SimulatedDockingApi;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.discovery.SableSubLevelResidency;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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

@GameTestHolder("gadgetsngizmos")
@PrefixGameTestTemplate(false)
public final class LoadRecoveryGameTests{
    private LoadRecoveryGameTests(){}

    @GameTest(template = "empty_container", batch = "scm_load_recovery")
    public static void savedDockSurvivesAnUnavailablePeer(GameTestHelper ctx){
        BlockPos pos = new BlockPos(2, 3, 2);
        ctx.setBlock(pos, BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(
                "simulated", "docking_connector")).defaultBlockState());
        var connector = (DockingConnectorBlockEntity)ctx.getBlockEntity(pos);
        BlockPos peer = new BlockPos(20000000, 100, 20000000);
        connector.otherConnectorPosition = peer;
        ctx.assertTrue(connector instanceof SimulatedDockingApi.PhysicsState,
                "Docking restoration compatibility mixin was not applied");
        ctx.assertTrue(!ctx.getLevel().hasChunkAt(peer), "Peer fixture unexpectedly loaded");
        connector.tick();
        ctx.assertTrue(peer.equals(connector.otherConnectorPosition),
                "An unavailable peer was treated as a broken docking connection");
        connector.otherConnectorPosition = null;
        ctx.succeed();
    }

    @GameTest(template = "empty_container", batch = "scm_load_recovery")
    public static void reciprocalSavedDockRecreatesItsNativeConstraint(GameTestHelper ctx){
        var level = ctx.getLevel();
        var container = SubLevelContainer.getContainer(level);
        var body = (ServerSubLevel)container.allocateNewSubLevel(new Pose3d());
        var residency = SableSubLevelResidency.lease("gadgetsngizmos:load_recovery_test");
        residency.retain(body);
        DockingConnectorBlockEntity ship = null;
        DockingConnectorBlockEntity dock = null;
        try{
            BlockPos station = new BlockPos(2, 2, 2);
            var block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("simulated", "docking_connector"));
            ctx.setBlock(station, block.defaultBlockState().setValue(BlockStateProperties.POWERED, true)
                    .setValue(BlockStateProperties.FACING, Direction.WEST));
            dock = (DockingConnectorBlockEntity)ctx.getBlockEntity(station);
            ctx.setBlock(station.east(), Blocks.REDSTONE_BLOCK);
            var plot = body.getPlot();
            BlockPos center = new BlockPos(plot.getCenterChunk().getMinBlockX() + 8,
                    plot.getCenterBlock().getY(), plot.getCenterChunk().getMinBlockZ() + 8);
            plot.newEmptyChunk(plot.getCenterChunk());
            var chunk = plot.getLoadedChunks().iterator().next().getChunk();
            chunk.setBlockState(center, block.defaultBlockState().setValue(BlockStateProperties.POWERED, true)
                    .setValue(BlockStateProperties.FACING, Direction.EAST), false);
            chunk.setBlockState(center.west(), Blocks.REDSTONE_BLOCK.defaultBlockState(), false);
            ship = (DockingConnectorBlockEntity)SubLevelBlockEntityCollector.getOrCreateBlockEntity(body, center);
            ctx.assertTrue(ship != null, "Native ship connector fixture is missing at " + center);
            container.physicsSystem().getPipeline().onStatsChanged(body);
            BlockPos world = ctx.absolutePos(station);
            Pose3d pose = new Pose3d();
            pose.position().set(world.getX() - 2, world.getY(), world.getZ());
            pose.rotationPoint().set(center.getX(), center.getY(), center.getZ());
            body.logicalPose().set(pose);
            body.updateLastPose();
            body.updateBoundingBox();
            ctx.assertTrue(SimulatedDockingApi.isEndpointLoaded(level, body.getUniqueId(), center),
                    "Loaded plot connector was reported unavailable");
            ship.powered = true;
            dock.powered = true;
            ship.extension.setValue(1.0D);
            dock.extension.setValue(1.0D);
            ship.feet.setValue(1.0D);
            dock.feet.setValue(1.0D);
            ship.otherConnectorPosition = dock.getBlockPos();
            dock.otherConnectorPosition = ship.getBlockPos();
            dock.otherConnectorSubLevelId = body.getUniqueId();
            ctx.assertTrue(!SimulatedDockingApi.hasPhysicalConnection(ship), "Fixture already had a constraint");
            ctx.assertTrue(body.getUniqueId().equals(SableLevelApi.containingId(ship)), "Ship connector has no native body ownership");
            // Earlier fixtures may have removed another body from this reused plot slot.
            for(int idx = 0; idx < 5; idx++) level.getChunkAt(ctx.absolutePos(new BlockPos(idx * 16, 0, 0)));
            ctx.assertTrue(level.getBlockEntity(center) == ship, "Native pair cannot resolve the loaded ship connector: actual=" + level.getBlockEntity(center)
                            + ", expected=" + ship + ", chunk=" + chunk.getPos()
                            + ", center=" + center + ", state=" + level.getBlockState(center));
            ctx.assertTrue(ship.isExtended() && dock.isExtended(), "Saved connector extension was not restored");
            SimulatedDockingApi.restoreConnection(ship);
            ctx.assertTrue(((SimulatedDockingApi.PhysicsState)ship).isDockingConstraintPending()
                    || SimulatedDockingApi.hasPhysicalConnection(ship), "Native restoration did not create a constraint");
            var savedShip = ship;
            var savedDock = dock;
            ctx.runAfterDelay(3, () -> {
                try{
                    ctx.assertTrue(SimulatedDockingApi.hasPhysicalConnection(savedDock),
                            "Restored peer has no live native joint");
                    ctx.assertTrue(SimulatedDockingApi.restoreConnection(savedShip),
                            "Repeated restoration lost the live joint");
                }finally{
                    savedShip.unDock();
                    savedDock.unDock();
                    residency.close();
                    if(container.getSubLevel(body.getUniqueId()) == body) SableSubLevelLifecycleApi.remove(level, List.of(body));
                }
                ctx.succeed();
            });
        }catch(RuntimeException | Error err){
            if(ship != null) ship.unDock();
            if(dock != null) dock.unDock();
            residency.close();
            if(container.getSubLevel(body.getUniqueId()) == body) SableSubLevelLifecycleApi.remove(level, List.of(body));
            throw err;
        }
    }

    @GameTest(template = "empty_container", batch = "scm_load_recovery")
    public static void groundRecoveryStillRejectsAnOverheadBlock(GameTestHelper ctx){
        BlockPos floor = new BlockPos(1, 1, 1);
        ctx.setBlock(floor, Blocks.STONE);
        BlockPos world = ctx.absolutePos(floor);
        AABB hull = new AABB(world.getX() + 0.1D, world.getY() + 0.7D, world.getZ() + 0.1D,
                world.getX() + 0.9D, world.getY() + 1.4D, world.getZ() + 0.9D);
        Vec3 climb = new Vec3(0.1D, 1.0D, 0.0D);
        double clear = SubLevelParticleOcclusion.findAirOnlyDepartureBlockingDistance(
                ctx.getLevel(), climb, 1.5D, List.of(hull), true, Set.of(), 36, null, 0.1D);
        var scan = SubLevelParticleOcclusion.beginDepartureBoundsBlockingDistanceScan(
                ctx.getLevel(), null, hull.getCenter(), climb, 1.5D, List.of(hull),
                true, Set.of(), true, 0.1D);
        for(int idx = 0; idx < 1000 && !scan.isComplete(); idx++) scan.advance(16, 0L);
        ctx.assertTrue(scan.isComplete() && scan.result() >= 1.5D,
                "Exact hull scan prevented ground departure: " + scan.result());
        ctx.assertTrue(clear >= 1.5D, "Initial floor overlap prevented an outward climb: " + clear);
        ctx.setBlock(floor.above(2), Blocks.STONE);
        double blocked = SubLevelParticleOcclusion.findAirOnlyDepartureBlockingDistance(
                ctx.getLevel(), climb, 1.5D, List.of(hull), true, Set.of(), 36, null, 0.1D);
        scan = SubLevelParticleOcclusion.beginDepartureBoundsBlockingDistanceScan(
                ctx.getLevel(), null, hull.getCenter(), climb, 1.5D, List.of(hull),
                true, Set.of(), true, 0.1D);
        for(int idx = 0; idx < 1000 && !scan.isComplete(); idx++) scan.advance(16, 0L);
        ctx.assertTrue(scan.isComplete() && scan.result() > 0.0D && scan.result() < 1.0D,
                "Exact recovery scan ignored the ceiling: " + scan.result());
        ctx.assertTrue(blocked > 0.0D && blocked < 1.0D,
                "Ground recovery ignored a new overhead obstruction: " + blocked);
        ctx.succeed();
    }
}
