package com.rieno.gadgetsandgizmos.lib.navigation;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

// Exercise complete spatial bypasses and moving opposing vehicles with swept body clearance
class ObstacleDetourPlannerTest{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @Test void sidestepsAnObstacleWhichBlocksForwardDiagonals(){
        List<AABB> obstacles = List.of(new AABB(-3, -2, 0.6D, 3, 2, 8));
        Vec3 start = Vec3.ZERO;
        Vec3 rejoin = new Vec3(0, 0, 12);
        var plan = plan(start, rejoin, 2, 6, false, true, obstacles);
        assertEquals(3, plan.size());
        assertEquals(0.0D, plan.getFirst().z);
        assertTrue(Math.abs(plan.getFirst().x) > 3.4D);
        assertEquals(rejoin, plan.getLast());
        assertClear(start, plan, obstacles);
    }

    @Test void fliesOverWhenTheSidesAndUndersideAreBlocked(){
        List<AABB> obstacles = List.of(new AABB(-100, -4, 2, 100, 2, 8),
                new AABB(-100, -10, -100, 100, -2, 100));
        Vec3 rejoin = new Vec3(0, 0, 12);
        var plan = plan(Vec3.ZERO, rejoin, 2, 1.5D, true, true, obstacles);
        assertEquals(3, plan.size());
        assertTrue(plan.getFirst().y > 2.4D);
        assertClear(Vec3.ZERO, plan, obstacles);
    }

    @Test void fliesUnderWhenTheSidesAndOverheadAreBlocked(){
        List<AABB> obstacles = List.of(new AABB(-100, -2, 2, 100, 4, 8),
                new AABB(-100, 4.5D, -100, 100, 10, 100));
        Vec3 rejoin = new Vec3(0, 0, 12);
        var plan = plan(Vec3.ZERO, rejoin, 2, 1.5D, true, false, obstacles);
        assertEquals(3, plan.size());
        assertTrue(plan.getFirst().y < -2.4D);
        assertClear(Vec3.ZERO, plan, obstacles);
    }

    @Test void neverAcceptsAnEscapeWhoseReturnLegIsBlocked(){
        var queued = ObstacleDetourPlanner.queue(request(Vec3.ZERO, new Vec3(0, 0, 12), 2, 2, true, true));
        queued.advance((start, end) -> end.distanceToSqr(new Vec3(0, 0, 12)) > 1.0E-8D, 1000, () -> true);
        assertTrue(queued.finished());
        assertTrue(queued.waypoints().isEmpty());
    }

    @Test void retainsValidationAcrossTickBudgets(){
        var queued = ObstacleDetourPlanner.queue(request(Vec3.ZERO, new Vec3(0, 0, 12), 2, 3, true, true));
        AtomicInteger probes = new AtomicInteger();
        ObstacleDetourPlanner.Probe probe = (start, end) -> { probes.incrementAndGet(); return true; };
        queued.advance(probe, 1, () -> false);
        assertEquals(0, probes.get());
        for(int tick = 0; tick < 3; tick++){
            queued.advance(probe, 1, () -> true);
            assertEquals(tick + 1, probes.get());
            assertEquals(tick == 2, queued.finished());
        }
        assertEquals(3, queued.waypoints().size());
        queued.advance(probe, 10, () -> true);
        assertEquals(3, probes.get());
    }

    @Test void choosesAControllableVerticalPassWhenLateralAuthorityIsAbsent(){
        var queued = ObstacleDetourPlanner.queue(request(Vec3.ZERO, new Vec3(0, 0, 12), 1, 2, true, true));
        queued.advance((start, end) -> Math.abs(end.x - start.x) < 1.0E-8D, 1000, () -> true);
        assertFalse(queued.waypoints().isEmpty());
        assertEquals(0.0D, queued.waypoints().getFirst().x);
        assertTrue(queued.waypoints().getFirst().y > 0.0D);
    }

    // Close the selected horizontal or vertical pass before requesting its replacement
    @Test void rechecksTheSceneWhenANewObstacleClosesTheChosenPass(){
        Vec3 rejoin = new Vec3(0, 0, 12);
        List<AABB> initial = List.of(new AABB(-3, -2, 1, 3, 2, 8));
        var first = plan(Vec3.ZERO, rejoin, 2, 8, true, true, initial);
        assertFalse(first.isEmpty());
        Vec3 pass = first.getFirst();
        List<AABB> changed = List.of(initial.getFirst(),
                new AABB(pass.x - 0.5D, pass.y - 0.5D, 3, pass.x + 0.5D, pass.y + 0.5D, 7));
        var replacement = plan(Vec3.ZERO, rejoin, 2, 8, true, true, changed);
        assertFalse(replacement.isEmpty());
        assertNotEquals(first, replacement);
        assertClear(Vec3.ZERO, replacement, changed);
    }

    // Keep opposing ships on complementary sides of either a horizontal or vertical pass
    @Test void opposingStoppedShipsPassWithoutCollidingOrWaitingForEachOther(){
        Vec3 first = new Vec3(0, 0, -8);
        Vec3 second = new Vec3(0, 0, 8);
        Vec3 firstGoal = new Vec3(0, 0, 12);
        Vec3 secondGoal = new Vec3(0, 0, -12);
        var firstPlan = plan(first, firstGoal, 2, 6, true, true, List.of(body(second)));
        var secondPlan = plan(second, secondGoal, 2, 6, true, false, List.of(body(first)));
        assertFalse(firstPlan.isEmpty());
        assertFalse(secondPlan.isEmpty());
        assertTrue(firstPlan.getFirst().x * secondPlan.getFirst().x < 0.0D
                || firstPlan.getFirst().y * secondPlan.getFirst().y < 0.0D);
        int firstIdx = 0;
        int secondIdx = 0;
        for(int tick = 0; tick < 800 && (firstIdx < firstPlan.size() || secondIdx < secondPlan.size()); tick++){
            Vec3 nextFirst = firstIdx < firstPlan.size() ? move(first, firstPlan.get(firstIdx)) : first;
            Vec3 nextSecond = secondIdx < secondPlan.size() ? move(second, secondPlan.get(secondIdx)) : second;
            assertTrue(clear(first, nextFirst, List.of(body(second))));
            assertTrue(clear(second, nextSecond, List.of(body(nextFirst))));
            assertFalse(body(nextFirst).intersects(body(nextSecond)));
            first = nextFirst;
            second = nextSecond;
            if(firstIdx < firstPlan.size() && first.distanceToSqr(firstPlan.get(firstIdx)) < 1.0E-8D) firstIdx++;
            if(secondIdx < secondPlan.size() && second.distanceToSqr(secondPlan.get(secondIdx)) < 1.0E-8D) secondIdx++;
        }
        assertEquals(firstGoal, first);
        assertEquals(secondGoal, second);
    }

    @Test void headOnTrafficRequestsABypassBeforePhysicalContact(){
        UUID firstId = new UUID(0, 1);
        UUID secondId = new UUID(0, 2);
        Vec3 first = new Vec3(0, 0, -8);
        Vec3 second = new Vec3(0, 0, 8);
        var a = new RouteTrafficPriority.Participant(firstId, first, Vec3.ZERO,
                List.of(first, new Vec3(0, 0, 12)), SablePathfinder.RouteMode.FLIGHT, 1, 1, 2);
        var b = new RouteTrafficPriority.Participant(secondId, second, Vec3.ZERO,
                List.of(second, new Vec3(0, 0, -12)), SablePathfinder.RouteMode.FLIGHT, 1, 1, 2);
        var decisionA = RouteTrafficPriority.resolve(a, List.of(b), RouteTrafficPriority.Settings.DEFAULT);
        var decisionB = RouteTrafficPriority.resolve(b, List.of(a), RouteTrafficPriority.Settings.DEFAULT);
        assertTrue(RouteTrafficPriority.requestsDetour(decisionA));
        assertTrue(RouteTrafficPriority.requestsDetour(decisionB));
        assertNotEquals(decisionA.yield(), decisionB.yield());
        assertNotEquals(RouteTrafficPriority.prefersUpperPass(firstId, secondId),
                RouteTrafficPriority.prefersUpperPass(secondId, firstId));
        assertFalse(RouteTrafficPriority.requestsDetour(RouteTrafficPriority.Decision.clear()));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static ObstacleDetourPlanner.Request request(Vec3 start, Vec3 rejoin, double width,
            double height, boolean spatial, boolean upper){
        return new ObstacleDetourPlanner.Request(start, rejoin, rejoin.subtract(start), width, height, 32, spatial, upper);
    }

    private static List<Vec3> plan(Vec3 start, Vec3 rejoin, double width, double height,
            boolean spatial, boolean upper, List<AABB> obstacles){
        var queued = ObstacleDetourPlanner.queue(request(start, rejoin, width, height, spatial, upper));
        queued.advance((from, to) -> clear(from, to, obstacles), 1000, () -> true);
        assertTrue(queued.finished());
        return queued.waypoints();
    }

    private static void assertClear(Vec3 start, List<Vec3> plan, List<AABB> obstacles){
        for(Vec3 point : plan){
            assertTrue(clear(start, point, obstacles));
            start = point;
        }
    }

    private static boolean clear(Vec3 start, Vec3 end, List<AABB> obstacles){
        for(AABB obstacle : obstacles){
            AABB expanded = obstacle.inflate(0.4D);
            if(expanded.contains(start) || expanded.contains(end) || expanded.clip(start, end).isPresent()) return false;
        }
        return true;
    }

    private static AABB body(Vec3 pos){
        return new AABB(pos.x - 1, pos.y - 1, pos.z - 1, pos.x + 1, pos.y + 1, pos.z + 1);
    }

    private static Vec3 move(Vec3 start, Vec3 target){
        Vec3 offset = target.subtract(start);
        return offset.length() <= 0.1D ? target : start.add(offset.normalize().scale(0.1D));
    }
}
