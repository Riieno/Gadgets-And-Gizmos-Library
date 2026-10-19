package com.rieno.gadgetsandgizmos.lib.navigation;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.physics.SubLevelParticleOcclusion;
import com.rieno.gadgetsandgizmos.lib.scm.ScmBuiltinControlModes;
import com.rieno.gadgetsandgizmos.lib.scm.ScmControlMode;
import com.rieno.gadgetsandgizmos.lib.scm.ScmControlModeRegistry;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Exercise complete hull clearance above, below and inside enclosed spatial routes
class SpatialAirshipNavigationTest{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           CONSTANTS
    ========================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final double PADDING = 0.125D;
    private static final AABB HULL = new AABB(-0.5D, -0.5D, -0.5D, 0.5D, 0.5D, 0.5D);
    private static final Vec3 FORWARD = new Vec3(0, 0, 1);
    private static final Vec3 GOAL = new Vec3(0, 0, 10);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    ========================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Find smaller vertical passes which fit between the tunnel floor and ceiling
    @Test void localBypassesFitAboveAndBelowBlockersInsideATunnel(){
        for(boolean over : new boolean[]{true, false}){
            List<AABB> obstacles = tunnel();
            obstacles.add(over ? new AABB(-0.8D, -1.5D, 2, 0.8D, -0.2D, 8)
                    : new AABB(-0.8D, 0.2D, 2, 0.8D, 1.5D, 8));
            var plan = ObstacleDetourPlanner.queue(new ObstacleDetourPlanner.Request(
                    Vec3.ZERO, GOAL, FORWARD, 3, 3, 32, true, over));
            plan.advance((start, end) -> clear(start, end, obstacles), 1000, () -> true);
            assertTrue(plan.finished());
            assertFalse(plan.waypoints().isEmpty());
            assertTrue(over ? plan.waypoints().getFirst().y > 0 : plan.waypoints().getFirst().y < 0);
            assertTrue(Math.abs(plan.waypoints().getFirst().y) < 1.0D);
            assertClear(plan.waypoints(), obstacles);
        }
    }

    // Refine the grid when alternating low and high blockers require different vertical passes
    @Test void refinesThroughAlternatingBlockersWithWallsOnEverySide(){
        List<AABB> obstacles = tunnel();
        obstacles.add(new AABB(-0.8D, -1.5D, 2, 0.8D, -0.2D, 3));
        obstacles.add(new AABB(-0.8D, 0.2D, 6, 0.8D, 1.5D, 7));
        var req = request(GOAL, 1, query -> clear(query.start(), query.end(), obstacles)
                ? SablePathfinder.Traversal.clear(query.mode()) : SablePathfinder.Traversal.blocked());
        var coarse = SablePathfinder.queue(req);
        advance(coarse);
        assertFalse(coarse.result().reachedDestination());
        var refined = SablePathfinder.queueRefining(req, 0.125D);
        advance(refined);
        assertTrue(refined.result().reachedDestination());
        List<Vec3> route = refined.result().waypoints().stream().map(SablePathfinder.Waypoint::position).toList();
        assertTrue(route.stream().anyMatch(pos -> pos.y > 0.4D));
        assertTrue(route.stream().anyMatch(pos -> pos.y < -0.4D));
        assertEquals(GOAL, route.getLast());
        assertClear(route, obstacles);
    }

    // Permit an initial pure ascent or descent when a forward diagonal would hit the blocker
    @Test void verticalEscapeDoesNotRequireForwardRoom(){
        for(boolean over : new boolean[]{true, false}){
            List<AABB> obstacles = tunnel();
            obstacles.add(over ? new AABB(-0.8D, -1.5D, 0.7D, 0.8D, -0.2D, 3)
                    : new AABB(-0.8D, 0.2D, 0.7D, 0.8D, 1.5D, 3));
            var preferences = new SablePathfinder.RoutePreferences(FORWARD,
                    0.15D, 1, 3, 0.2D, -1, 0);
            var req = new SablePathfinder.Request(null, SablePathfinder.Location.world(Vec3.ZERO),
                    SablePathfinder.Location.world(GOAL), SablePathfinder.Safety.DEFAULT,
                    SablePathfinder.Movement.FLIGHT, 1, 16, 4096, 0.25D,
                    query -> clear(query.start(), query.end(), obstacles)
                    ? SablePathfinder.Traversal.clear(query.mode()) : SablePathfinder.Traversal.blocked(), preferences);
            var plan = SablePathfinder.queueRefining(req, 0.125D);
            advance(plan);
            assertTrue(plan.result().reachedDestination());
            List<Vec3> route = plan.result().waypoints().stream().map(SablePathfinder.Waypoint::position).toList();
            assertTrue(over ? route.getFirst().y > 0 : route.getFirst().y < 0);
            assertClear(route, obstacles);
        }
    }

    // Keep both ascent and descent propulsion available to the Airship controller
    @Test void airshipProducesVerticalThrustInBothDirections(){
        for(double sign : new double[]{1, -1}){
            Vec3 dir = new Vec3(0, sign, 0);
            var input = new ScmControlMode.ControlInput(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO,
                    FORWARD, new Vec3(0, 1, 0), new Vec3(1, 0, 0), dir.scale(4), dir, Vec3.ZERO,
                    2, 0.25D, 0.6D, true, 8, 8, 2, -1, true, true, false, 0, 0, null);
            var output = ScmControlModeRegistry.resolve(ScmBuiltinControlModes.AIRSHIP_ID.toString()).navigate(input);
            assertTrue(output.force().dot(dir) > 0.0D);
        }
    }

    // Reject an enclosure which is smaller than the physical ship in every vertical pass
    @Test void rejectsATunnelTooSmallForTheHull(){
        List<AABB> obstacles = List.of(new AABB(-20, 0.45D, -20, 20, 20, 20),
                new AABB(-20, -20, -20, 20, -0.45D, 20));
        var plan = SablePathfinder.queueRefining(request(GOAL, 1,
                query -> clear(query.start(), query.end(), obstacles)
                ? SablePathfinder.Traversal.clear(query.mode()) : SablePathfinder.Traversal.blocked()), 0.125D);
        advance(plan);
        assertFalse(plan.result().reachedDestination());
    }

    // Bound retained refinement work across ticks and across every resolution stage
    @Test void sharesTheExpansionLimitAcrossRefinements(){
        var req = new SablePathfinder.Request(null, SablePathfinder.Location.world(Vec3.ZERO),
                SablePathfinder.Location.world(GOAL), null, SablePathfinder.Movement.FLIGHT,
                2, 16, 32, 0.25D, query -> SablePathfinder.Traversal.blocked());
        var plan = SablePathfinder.queueRefining(req, 0.125D);
        int prev = 0;
        for(int tick = 0; tick < 32 && !plan.finished(); tick++){
            plan.advance(1);
            int next = plan.result().expandedNodes();
            assertTrue(next >= prev && next - prev <= 1);
            assertTrue(plan.progress() >= 0 && plan.progress() <= 1);
            prev = next;
        }
        assertTrue(plan.finished());
        assertFalse(plan.result().reachedDestination());
        assertTrue(plan.result().expandedNodes() <= req.maximumExpansions());
        assertEquals(1.0D, plan.progress());
    }

    // Keep a nearby climb until the control point actually completes it
    @Test void capturesShortBendsByPointProgressRatherThanHullOverlap(){
        Vec3 climb = new Vec3(0, 0.375D, 0);
        List<Vec3> route = List.of(climb, new Vec3(0, 0.375D, 4));
        assertEquals(0, SablePathfinder.advanceWaypointProgress(route, 0,
                Vec3.ZERO, Vec3.ZERO, 0.0625D).nextWaypointIndex());
        assertEquals(1, SablePathfinder.advanceWaypointProgress(route, 0,
                Vec3.ZERO, climb, 0.0625D).nextWaypointIndex());
        assertEquals(0, SablePathfinder.advanceWaypointProgress(List.of(new Vec3(0, 0, 0.4D)),
                0, Vec3.ZERO, new Vec3(0, 0.4D, 0.4D), 0.0625D).nextWaypointIndex());
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           HELPERS
    ========================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Enclose the route with walls, a floor and a ceiling beyond the search horizon
    private static List<AABB> tunnel(){
        return new ArrayList<>(List.of(new AABB(-20, -20, -20, -0.8D, 20, 20),
                new AABB(0.8D, -20, -20, 20, 20, 20),
                new AABB(-20, -20, -20, 20, -1.5D, 20),
                new AABB(-20, 1.5D, -20, 20, 20, 20)));
    }

    // Configure a spatial request without requiring a Minecraft level for synthetic geometry
    private static SablePathfinder.Request request(Vec3 target, double spacing, SablePathfinder.Validator validator){
        return new SablePathfinder.Request(null, SablePathfinder.Location.world(Vec3.ZERO),
                SablePathfinder.Location.world(target), SablePathfinder.Safety.DEFAULT,
                SablePathfinder.Movement.FLIGHT, spacing, 16, 4096, 0.25D, validator);
    }

    // Sweep the complete padded hull instead of tracing its centre through an opening
    private static boolean clear(Vec3 start, Vec3 end, List<AABB> obstacles){
        double distance = start.distanceTo(end);
        return SubLevelParticleOcclusion.findEnvelopeBlockingDistance(end.subtract(start), distance,
                List.of(HULL.inflate(PADDING).move(start)), obstacles, PADDING) >= distance - 1.0E-6D;
    }

    // Validate every accepted leg through the same whole-hull geometry
    private static void assertClear(List<Vec3> route, List<AABB> obstacles){
        Vec3 start = Vec3.ZERO;
        for(Vec3 point : route){
            assertTrue(clear(start, point, obstacles), "Blocked leg from " + start + " to " + point);
            start = point;
        }
    }

    // Preserve retained work while advancing a bounded number of nodes per simulated tick
    private static void advance(SablePathfinder.QueuedPlan plan){
        for(int tick = 0; tick < 1000 && !plan.finished(); tick++) plan.advance(64);
        assertTrue(plan.finished());
    }
}
