package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.navigation.ObstacleDetourPlanner;
import com.rieno.gadgetsandgizmos.lib.navigation.SablePathfinder;
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

// Validate vertical and enclosed flight routes through loaded Minecraft collision geometry
@GameTestHolder("gadgetsngizmos")
@PrefixGameTestTemplate(false)
public final class SpatialNavigationGameTests{
    // Keep fixture state within each test's loaded level
    private SpatialNavigationGameTests(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           CONSTANTS
    ========================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final AABB HULL = new AABB(-0.625D, -0.625D, -0.625D, 0.625D, 0.625D, 0.625D);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    ========================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Choose a short climb or descent while the floor, ceiling and side walls remain blocking
    @GameTest(template = "empty_container")
    public static void verticalBypassesFitInsideLoadedTunnel(GameTestHelper ctx){
        BlockPos base = tunnel(ctx);
        Vec3 start = Vec3.atLowerCornerOf(base).add(2.5D, 2.5D, 1.75D);
        Vec3 target = Vec3.atLowerCornerOf(base).add(2.5D, 2.5D, 11);
        var validator = validator();
        for(boolean over : new boolean[]{true, false}){
            for(int x = 1; x <= 3; x++){
                for(int z = 4; z <= 5; z++){
                    ctx.getLevel().setBlock(base.offset(x, 1, z),
                            over ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
                    ctx.getLevel().setBlock(base.offset(x, 3, z),
                            over ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState(), 3);
                }
            }
            var plan = ObstacleDetourPlanner.queue(new ObstacleDetourPlanner.Request(
                    start, target, target.subtract(start), 3, 3, 6, true, over));
            plan.advance((from, to) -> clear(ctx, validator, from, to), 1000, () -> true);
            ctx.assertTrue(plan.finished() && !plan.waypoints().isEmpty(), "Vertical tunnel bypass was rejected");
            ctx.assertTrue(over ? plan.waypoints().getFirst().y > start.y
                    : plan.waypoints().getFirst().y < start.y, "Bypass chose the blocked vertical side");
            verify(ctx, validator, start, plan.waypoints());
        }
        ctx.succeed();
    }

    // Refine between coarse nodes to pass above one blocker and below the next in an enclosed tunnel
    @GameTest(template = "empty_container")
    public static void alternatingBlockersAllowACompleteEnclosedRoute(GameTestHelper ctx){
        BlockPos base = tunnel(ctx);
        for(int x = 1; x <= 3; x++){
            for(int z = 4; z <= 5; z++) ctx.getLevel().setBlock(base.offset(x, 1, z), Blocks.STONE.defaultBlockState(), 3);
            for(int z = 8; z <= 9; z++) ctx.getLevel().setBlock(base.offset(x, 3, z), Blocks.STONE.defaultBlockState(), 3);
        }
        Vec3 start = Vec3.atLowerCornerOf(base).add(2.5D, 2.5D, 1.75D);
        Vec3 target = Vec3.atLowerCornerOf(base).add(2.5D, 2.5D, 11);
        var validator = validator();
        var req = request(ctx, validator, start, target);
        var coarse = SablePathfinder.queue(req);
        advance(coarse);
        ctx.assertTrue(!coarse.result().reachedDestination(), "Coarse fixture did not require refinement");
        var plan = SablePathfinder.queueRefining(req, 0.125D);
        advance(plan);
        ctx.assertTrue(plan.result().reachedDestination(), "Enclosed spatial route was not found");
        List<Vec3> route = plan.result().waypoints().stream().map(SablePathfinder.Waypoint::position).toList();
        ctx.assertTrue(route.stream().anyMatch(pos -> pos.y > start.y + 0.25D)
                && route.stream().anyMatch(pos -> pos.y < start.y - 0.25D), "Route omitted an over or under pass");
        verify(ctx, validator, start, route);
        ctx.succeed();
    }

    // Refuse to cross a tunnel whose entire cross-section is physically sealed
    @GameTest(template = "empty_container")
    public static void sealedTunnelRemainsBlocked(GameTestHelper ctx){
        BlockPos base = tunnel(ctx);
        for(int x = 1; x <= 3; x++){
            for(int y = 1; y <= 3; y++) ctx.getLevel().setBlock(base.offset(x, y, 4), Blocks.STONE.defaultBlockState(), 3);
        }
        Vec3 start = Vec3.atLowerCornerOf(base).add(2.5D, 2.5D, 1.75D);
        Vec3 target = Vec3.atLowerCornerOf(base).add(2.5D, 2.5D, 11);
        var plan = SablePathfinder.queueRefining(request(ctx, validator(), start, target), 0.125D);
        advance(plan);
        ctx.assertTrue(!plan.result().reachedDestination(), "Sealed tunnel was accepted as traversable");
        ctx.succeed();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           HELPERS
    ========================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Build closed end caps and four solid tunnel faces above the test structure
    private static BlockPos tunnel(GameTestHelper ctx){
        BlockPos base = ctx.absolutePos(new BlockPos(1, 10, 1));
        for(int x = 0; x <= 4; x++){
            for(int y = 0; y <= 4; y++){
                for(int z = 0; z <= 12; z++){
                    boolean wall = x == 0 || x == 4 || y == 0 || y == 4 || z == 0 || z == 12;
                    ctx.getLevel().setBlock(base.offset(x, y, z),
                            wall ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        return base;
    }

    // Use the public compound-hull policy with the same padding as SCM flight control
    private static SablePathfinder.Validator validator(){
        return SablePathfinder.compoundCollisionValidator(new SablePathfinder.CollisionOptions(
                true, true, Set.of(), 16), query -> List.of(HULL.move(query.start())), 0.125D);
    }

    // Keep flight movement free of any initial forward commitment
    private static SablePathfinder.Request request(GameTestHelper ctx, SablePathfinder.Validator validator,
            Vec3 start, Vec3 target){
        return new SablePathfinder.Request(ctx.getLevel(), SablePathfinder.Location.world(start),
                SablePathfinder.Location.world(target), SablePathfinder.Safety.DEFAULT,
                SablePathfinder.Movement.FLIGHT, 2, 16, 4096, 0.25D, validator);
    }

    // Validate one complete translated hull against the current loaded tunnel
    private static boolean clear(GameTestHelper ctx, SablePathfinder.Validator validator, Vec3 start, Vec3 end){
        return validator.validate(new SablePathfinder.Query(ctx.getLevel(), start, end,
                SablePathfinder.Safety.DEFAULT, SablePathfinder.RouteMode.FLIGHT)).result()
                == SablePathfinder.TraversalResult.CLEAR;
    }

    // Recheck every emitted leg using the live physical collision policy
    private static void verify(GameTestHelper ctx, SablePathfinder.Validator validator, Vec3 start, List<Vec3> route){
        for(Vec3 point : route){
            ctx.assertTrue(clear(ctx, validator, start, point), "Route intersects a tunnel blocker");
            start = point;
        }
    }

    // Retain the same queued plan while spreading its node work across bounded advances
    private static void advance(SablePathfinder.QueuedPlan plan){
        for(int tick = 0; tick < 1000 && !plan.finished(); tick++) plan.advance(64);
    }
}
