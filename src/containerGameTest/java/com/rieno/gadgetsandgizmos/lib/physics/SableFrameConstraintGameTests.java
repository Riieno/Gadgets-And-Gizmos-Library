package com.rieno.gadgetsandgizmos.lib.physics;

import com.rieno.gadgetsandgizmos.lib.discovery.SableSubLevelResidency;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;

// Exercise retained joints against native physics with a group of stationary attachments
@GameTestHolder("gadgetsngizmos")
@PrefixGameTestTemplate(false)
public final class SableFrameConstraintGameTests{
    private SableFrameConstraintGameTests(){}

    @GameTest(template = "empty_container", timeoutTicks = 120)
    public static void manyAttachmentsHoldAndFollowChangedFrames(GameTestHelper ctx){
        var level = ctx.getLevel();
        var container = SubLevelContainer.getContainer(level);
        var pipeline = container.physicsSystem().getPipeline();
        var residency = SableSubLevelResidency.lease("gadgetsngizmos:frame_constraint_test");
        var bodies = new ArrayList<ServerSubLevel>();
        var attachments = new ArrayList<SableFrameConstraint>();
        var anchors = new ArrayList<Vector3d>();
        var localAnchors = new ArrayList<Vector3d>();
        var rotation = new Quaterniond();
        try{
            for(int idx = 0; idx < 16; idx++){
                var worldPos = new BlockPos(100 + idx % 4 * 2, 256, 100 + idx / 4 * 2);
                var pose = new Pose3d();
                pose.position().set(worldPos.getX(), worldPos.getY(), worldPos.getZ());
                var body = (ServerSubLevel)container.allocateNewSubLevel(pose);
                bodies.add(body);
                residency.retain(body);
                var plot = body.getPlot();
                plot.newEmptyChunk(plot.getCenterChunk());
                plot.getLoadedChunks().iterator().next().getChunk()
                        .setBlockState(plot.getCenterBlock(), Blocks.STONE.defaultBlockState(), false);
                pipeline.onStatsChanged(body);
                var local = new Vector3d(body.logicalPose().rotationPoint());
                var anchor = new Vector3d(body.logicalPose().position());
                var attachment = new SableFrameConstraint();
                attachments.add(attachment);
                anchors.add(anchor);
                localAnchors.add(local);
                ctx.assertTrue(attachment.update(pipeline, null, body, anchor, local, rotation),
                        "Native attachment was unavailable");
            }
        }catch(Throwable err){
            attachments.forEach(SableFrameConstraint::close);
            residency.close();
            SableSubLevelLifecycleApi.remove(level, bodies);
            throw err;
        }
        ctx.runAtTickTime(40, () -> {
            anchors.getFirst().add(1, 0, 0);
            attachments.getFirst().update(pipeline, null, bodies.getFirst(),
                    anchors.getFirst(), localAnchors.getFirst(), new Quaterniond().rotateY(0.5));
        });
        ctx.runAtTickTime(60, () -> attachments.getFirst().update(pipeline, null, bodies.getFirst(),
                anchors.getFirst(), localAnchors.getFirst(), new Quaterniond().rotateY(0.8)));
        ctx.runAtTickTime(80, () -> {
            try{
                for(int idx = 0; idx < bodies.size(); idx++){
                    var actual = bodies.get(idx).logicalPose().transformPosition(localAnchors.get(idx), new Vector3d());
                    ctx.assertTrue(actual.distance(anchors.get(idx)) < 0.15D,
                            "Solver attachment drifted: " + idx + " distance=" + actual.distance(anchors.get(idx)));
                    ctx.assertTrue(attachments.get(idx).isValid(), "Retained attachment was lost");
                }
                double alignment = Math.abs(bodies.getFirst().logicalPose().orientation().dot(new Quaterniond().rotateY(0.8)));
                ctx.assertTrue(alignment > 0.995D, "Orientation-only frame change was ignored");
                ctx.succeed();
            }finally{
                attachments.forEach(SableFrameConstraint::close);
                residency.close();
                SableSubLevelLifecycleApi.remove(level, bodies);
            }
        });
    }
}
