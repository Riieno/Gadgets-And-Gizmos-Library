package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.function.Supplier;

// Measure allocation reduction on a native plot while retaining an independent corner reference
@GameTestHolder("gadgetsngizmos")
@PrefixGameTestTemplate(false)
public final class CollisionQueryPerformanceGameTests{
    private static volatile double sink;
    private CollisionQueryPerformanceGameTests(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    ========================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @GameTest(template = "empty_container")
    public static void repeatedNativeBoundsAvoidGeometryAllocation(GameTestHelper ctx){
        var container = SubLevelContainer.getContainer(ctx.getLevel());
        ServerSubLevel body = (ServerSubLevel)container.allocateNewSubLevel(new Pose3d());
        try{
            var plot = body.getPlot(); plot.newEmptyChunk(plot.getCenterChunk());
            plot.getLoadedChunks().iterator().next().getChunk().setBlockState(plot.getCenterBlock(), Blocks.STONE.defaultBlockState(), false);
            container.physicsSystem().getPipeline().onStatsChanged(body);
            Pose3d pose = new Pose3d();
            var center = plot.getCenterBlock();
            pose.rotationPoint().set(center.getX(), center.getY(), center.getZ());
            pose.position().set(100, 100, 100);
            pose.orientation().rotateY(0.4D);
            body.logicalPose().set(pose);
            AABB bounds = SableAssemblyHullApi.worldBounds(body);
            AABB expected = reference(body);
            ctx.assertTrue(Math.abs(bounds.minX - expected.minX) < 1.0E-8D
                    && Math.abs(bounds.maxZ - expected.maxZ) < 1.0E-8D, "Native envelope changed");
            for(int idx = 0; idx < 10000; idx++){
                sink = reference(body).maxX;
                sink = SableAssemblyHullApi.worldBounds(body).maxX;
            }
            var mx = (com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
            if(mx.isThreadAllocatedMemorySupported()){
                if(!mx.isThreadAllocatedMemoryEnabled()) mx.setThreadAllocatedMemoryEnabled(true);
                Measurement baseline = measure(mx, () -> reference(body));
                Measurement optimized = measure(mx, () -> SableAssemblyHullApi.worldBounds(body));
                System.out.println("Collision bounds benchmark: reference=" + baseline + ", cached=" + optimized + ", queries=25000");
                ctx.assertTrue(optimized.bytes() <= baseline.bytes() / 4 + 16384,
                        "Repeated native bounds still allocate full geometry");
            }
            body.logicalPose().position().add(1, 0, 0);
            ctx.assertTrue(Math.abs(SableAssemblyHullApi.worldBounds(body).minX - bounds.minX - 1) < 1.0E-8D,
                    "A same-tick move retained stale bounds");
            ctx.succeed();
        }finally{
            if(container.getSubLevel(body.getUniqueId()) == body) SableSubLevelLifecycleApi.remove(ctx.getLevel(), List.of(body));
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    ========================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static Measurement measure(com.sun.management.ThreadMXBean mx, Supplier<AABB> query){
        long thread = Thread.currentThread().threadId();
        long bytes = mx.getThreadAllocatedBytes(thread), start = System.nanoTime();
        for(int idx = 0; idx < 25000; idx++) sink = query.get().maxX;
        return new Measurement(mx.getThreadAllocatedBytes(thread) - bytes, System.nanoTime() - start);
    }

    // Reproduce the original world-envelope corner pass without caching or matrix assumptions
    private static AABB reference(ServerSubLevel body){
        var bounds = body.getPlot().getBoundingBox();
        AABB res = null;
        for(double x : new double[]{bounds.minX(), bounds.maxX() + 1.0D}){
            for(double y : new double[]{bounds.minY(), bounds.maxY() + 1.0D}){
                for(double z : new double[]{bounds.minZ(), bounds.maxZ() + 1.0D}){
                    Vec3 point = body.logicalPose().transformPosition(new Vec3(x, y, z));
                    AABB corner = new AABB(point, point);
                    res = res == null ? corner : res.minmax(corner);
                }
            }
        }
        return res;
    }

    private record Measurement(long bytes, long nanos){}
}
