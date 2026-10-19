package com.rieno.gadgetsandgizmos.lib.physics;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Check telemetry and transfer guards against a native body with no block entity anchor
@GameTestHolder("gadgetsngizmos")
@PrefixGameTestTemplate(false)
public final class SableLifecycleTelemetryGameTests{
    // Keep native body setup on the test entry point
    private SableLifecycleTelemetryGameTests(){}

    // Retain mass and gravity without an anchor and defer empty-plot removal through transfer
    @GameTest(template = "empty_container")
    public static void anchorlessBodySurvivesTransferUntilCleanup(GameTestHelper ctx){
        var level = ctx.getLevel();
        var container = SubLevelContainer.getContainer(level);
        Pose3d pose = new Pose3d();
        BlockPos worldPos = ctx.absolutePos(new BlockPos(1, 4, 1));
        pose.position().set(worldPos.getX(), worldPos.getY(), worldPos.getZ());
        var body = (ServerSubLevel)container.allocateNewSubLevel(pose);
        try{
            var plot = body.getPlot();
            plot.newEmptyChunk(plot.getCenterChunk());
            var chunk = plot.getLoadedChunks().iterator().next().getChunk();
            BlockPos center = plot.getCenterBlock();
            chunk.setBlockState(center, Blocks.STONE.defaultBlockState(), false);
            container.physicsSystem().getPipeline().onStatsChanged(body);
            var snapshot = SablePhysicsTelemetry.sample(level, body.getUniqueId());
            ctx.assertTrue(snapshot.available() && snapshot.mass() > 0, "Anchorless telemetry lost body mass");
            ctx.assertTrue(snapshot.worldTotalForce().y() < 0, "Body telemetry omitted gravity");
            SableSubLevelLifecycleApi.transfer(level, body, () -> {
                ctx.assertTrue(SableSubLevelLifecycleApi.isTransferring(body), "Transfer scope was not retained");
                ctx.assertTrue(!body.isRemoved(), "Body was already removed before transfer");
                chunk.setBlockState(center, Blocks.AIR.defaultBlockState(), false);
                ctx.assertTrue(!body.isRemoved(), "Clearing the final block removed the retained body");
                body.onPlotBoundsChanged();
                ctx.assertTrue(!body.isRemoved(), "Native body was removed before transfer finished");
            });
            ctx.assertTrue(SableSubLevelLifecycleApi.removeEmpty(level, body), "Empty body was not cleaned up");
            ctx.succeed();
        }finally{
            if(container.getSubLevel(body.getUniqueId()) == body) SableSubLevelLifecycleApi.remove(level, java.util.List.of(body));
        }
    }
}
