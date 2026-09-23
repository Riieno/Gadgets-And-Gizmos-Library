package com.rieno.gadgetsandgizmos.lib.navigation;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroundPathPlannerTest {
    @Test
    void expiredRecoveryBudgetCannotCallWorldValidation(){
        var capabilities = new GroundPathPlanner.VehicleCapabilities(
                2.0D, Math.toRadians(32.0D), 0.0D, 0.0D, 0.0D, true);
        var request = new GroundPathPlanner.PoseRequest(Vec3.ZERO, new Vec3(0.0D, 0.0D, 10.0D),
                new Vec3(1.0D, 0.0D, 0.0D), capabilities, 20.0D, 1.0D, 192,
                (start, end) -> { throw new AssertionError("Validation after budget expiry"); });
        assertTrue(GroundPathPlanner.planRecoveryManeuver(request, () -> false).waypoints().isEmpty());
    }

    @Test
    void boundedMergeReturnsCertifiedProgressInsteadOfDiscardingIt(){
        var capabilities = new GroundPathPlanner.VehicleCapabilities(
                2.0D, Math.toRadians(32.0D), 0.0D, 0.0D, 0.0D, true);
        var checked = new java.util.concurrent.atomic.AtomicInteger();
        var request = new GroundPathPlanner.RouteRejoinRequest(Vec3.ZERO,
                new Vec3(1.0D, 0.0D, 0.0D), new Vec3(0.0D, 0.0D, 10.0D),
                new Vec3(0.0D, 0.0D, 1.0D), 0.0D, capabilities, 20.0D, 1.0D, 192,
                (start, end) -> { checked.incrementAndGet(); return true; });
        var plan = GroundPathPlanner.planRouteRejoin(request, () -> checked.get() < 3);
        assertFalse(plan.waypoints().isEmpty());
        assertFalse(plan.reachesGoal());
        assertTrue(plan.partial());
        assertEquals(3, checked.get());
        assertTrue(plan.routeSegments().stream().allMatch(GroundPathPlanner.Segment::clear));
    }

    @Test
    void splineSamplesExposeTheCompleteStraightForRouteAlignment() {
        double advance = GroundPathPlanner.forwardRouteMergeAdvance(
                Vec3.ZERO, new Vec3(1.0D, 0.0D, 0.0D),
                List.of(new Vec3(0.75D, 0.0D, 0.0D),
                        new Vec3(1.5D, 0.0D, 0.0D),
                        new Vec3(8.0D, 0.0D, 0.0D),
                        new Vec3(9.0D, 0.0D, 1.0D)),
                0, Math.toRadians(5.0D), 0.25D);

        assertEquals(8.0D, advance, 1.0E-6D);
    }

    @Test
    void routeTargetAheadKeepsTheOrderedSplineTangentAcrossLegs() {
        GroundPathPlanner.RouteTarget target = GroundPathPlanner.routeTargetAhead(
                Vec3.ZERO,
                List.of(new Vec3(1.0D, 0.0D, 0.0D),
                        new Vec3(2.0D, 0.0D, 0.0D),
                        new Vec3(2.0D, 0.0D, 1.0D),
                        new Vec3(2.0D, 0.0D, 2.0D)),
                0, 0.5D, 2.0D);

        assertTrue(target.found());
        assertEquals(2.0D, target.position().x, 1.0E-6D);
        assertEquals(0.5D, target.position().z, 1.0E-6D);
        assertEquals(2, target.nextWaypointIndex());
        assertEquals(0.5D, target.legProgress(), 1.0E-6D);
        assertEquals(0.0D, target.direction().x, 1.0E-6D);
        assertEquals(1.0D, target.direction().z, 1.0E-6D);
    }

    @Test
    void sidewaysRouteRejoinFinishesAlignedWithOrderedTravel() {
        GroundPathPlanner.VehicleCapabilities capabilities =
                new GroundPathPlanner.VehicleCapabilities(
                        2.0D, Math.toRadians(32.0D),
                        0.0D, 0.0D, 0.0D, true);
        GroundPathPlanner.Plan plan = GroundPathPlanner.planRouteRejoin(
                new GroundPathPlanner.RouteRejoinRequest(
                        new Vec3(0.0D, 0.0D, -4.0D),
                        new Vec3(1.0D, 0.0D, 0.0D), Vec3.ZERO,
                        new Vec3(0.0D, 0.0D, 1.0D), 8.0D,
                        capabilities, 20.0D, 1.0D, 192,
                        (from, to) -> true));

        assertTrue(plan.reachesGoal());
        assertFalse(plan.curves().isEmpty());
        assertTrue(plan.curves().getLast().vehicleForwardAtFraction(1.0D)
                .dot(new Vec3(0.0D, 0.0D, 1.0D)) >= Math.cos(Math.toRadians(12.0D)));
    }

    @Test
    void recoveryCanCommitReverseThenForwardAroundANewBlocker() {
        GroundPathPlanner.VehicleCapabilities capabilities =
                new GroundPathPlanner.VehicleCapabilities(
                        2.0D, Math.toRadians(32.0D),
                        0.0D, 0.0D, 0.0D, true);
        GroundPathPlanner.Plan plan = GroundPathPlanner.planRecoveryManeuver(
                new GroundPathPlanner.PoseRequest(
                        Vec3.ZERO, new Vec3(7.0D, 0.0D, 3.0D),
                        new Vec3(1.0D, 0.0D, 0.0D), capabilities,
                        14.0D, 1.0D, 2_048,
                        GroundPathPlannerTest::outsideBlocker));

        assertFalse(plan.waypoints().isEmpty());
        assertTrue(plan.waypoints().stream()
                .anyMatch(GroundPathPlanner.Waypoint::reverse));
        assertTrue(plan.waypoints().stream()
                .anyMatch(waypoint -> !waypoint.reverse()));
        GroundPathPlanner.Curve reverseCurve = plan.curves().stream()
                .filter(GroundPathPlanner.Curve::reverse)
                .findFirst().orElseThrow();
        assertTrue(reverseCurve.tangentAtFraction(0.5D).dot(
                reverseCurve.vehicleForwardAtFraction(0.5D)) < -0.999D);
    }

    @Test
    void exhaustedRouteRejoinBudgetReturnsSafeReverseProgress() {
        GroundPathPlanner.VehicleCapabilities capabilities =
                new GroundPathPlanner.VehicleCapabilities(
                        2.0D, Math.toRadians(32.0D),
                        0.0D, 0.0D, 0.0D, true);
        GroundPathPlanner.Plan plan = GroundPathPlanner.planRouteRejoin(
                new GroundPathPlanner.RouteRejoinRequest(
                        Vec3.ZERO, new Vec3(1.0D, 0.0D, 0.0D),
                        new Vec3(5.0D, 0.0D, 0.0D),
                        new Vec3(1.0D, 0.0D, 0.0D), 5.0D,
                        capabilities, 16.0D, 1.0D, 1,
                        (from, to) -> to.position().x <= 0.01D));

        assertFalse(plan.reachesGoal());
        assertTrue(plan.partial());
        assertFalse(plan.waypoints().isEmpty());
        assertTrue(plan.waypoints().stream()
                .allMatch(GroundPathPlanner.Waypoint::reverse));
    }

    private static boolean outsideBlocker(
            GroundPathPlanner.Pose from,
            GroundPathPlanner.Pose to
    ) {
        for (int sample = 0; sample <= 12; sample++) {
            double fraction = sample / 12.0D;
            Vec3 point = from.position().add(
                    to.position().subtract(from.position()).scale(fraction));
            if (point.x >= 0.2D && point.x <= 3.0D
                    && point.z >= -1.5D && point.z <= 1.5D) {
                return false;
            }
        }
        return true;
    }
}
