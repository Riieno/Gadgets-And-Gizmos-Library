package com.rieno.gadgetsandgizmos.lib.navigation;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SablePathfinderRouteProgressTest {
    @Test
    void overlapAdvanceCannotJumpAcrossAGapToACrossingSuffix() {
        List<Vec3> route = List.of(
                new Vec3(10.0D, 0.0D, 0.0D),
                new Vec3(10.0D, 0.0D, 10.0D),
                new Vec3(0.0D, 0.0D, 10.0D),
                new Vec3(0.0D, 0.0D, 0.0D));

        SablePathfinder.WaypointAdvance advance =
                SablePathfinder.advanceRouteOverlap(
                        route, 0, Vec3.ZERO,
                        new Vec3(5.0D, 0.0D, 10.0D),
                        new Vec3(5.0D, 0.0D, 10.0D), 0.0D,
                        (movementStart, movementEnd, legStart, legEnd, padding) ->
                                legStart.z == 10.0D && legEnd.z == 10.0D
                                        && legStart.distanceToSqr(legEnd) > 1.0E-8D);

        assertEquals(0, advance.nextWaypointIndex());
    }

    @Test
    void rejoinKeepsTheCurrentLegWhenALaterLegIsPhysicallyCloser() {
        List<SablePathfinder.Waypoint> route = List.of(
                ground(10.0D, 0.0D),
                ground(10.0D, 10.0D),
                ground(0.0D, 10.0D),
                ground(0.0D, 0.0D));

        SablePathfinder.RouteLegRejoinScan result =
                SablePathfinder.scanRouteLegRejoin(
                        null, route, 0, Vec3.ZERO,
                        new Vec3(0.0D, 0.0D, 4.0D),
                        SablePathfinder.Safety.DEFAULT,
                        query -> SablePathfinder.Traversal.clear(query.mode()),
                        route.size(), 0.6D);

        assertTrue(result.found());
        assertEquals(0, result.rejoin().waypointIndex());
        assertEquals(6.0D, result.rejoin().position().x, 1.0E-8D);
        assertEquals(0.0D, result.rejoin().position().z, 1.0E-8D);
    }

    @Test
    void equalDistanceCrossingKeepsTheEarliestForwardLeg() {
        List<SablePathfinder.Waypoint> route = List.of(
                ground(10.0D, 0.0D),
                ground(10.0D, 10.0D),
                ground(0.0D, 10.0D),
                ground(0.0D, 0.0D));

        SablePathfinder.RouteProjection projection =
                SablePathfinder.routeProjection(
                        route, 0, Vec3.ZERO, Vec3.ZERO, 0.0D);

        assertTrue(projection.found());
        assertEquals(0, projection.nextWaypointIndex());
        assertEquals(Vec3.ZERO, projection.position());
    }

    @Test
    void committedProgressExcludesPointsBehindTheVehicle() {
        List<SablePathfinder.Waypoint> route = List.of(ground(10.0D, 0.0D));

        SablePathfinder.RouteLegRejoinScan result =
                SablePathfinder.scanRouteLegRejoin(
                        null, route, 0, Vec3.ZERO,
                        new Vec3(2.0D, 0.0D, 1.0D),
                        SablePathfinder.Safety.DEFAULT,
                        query -> SablePathfinder.Traversal.clear(query.mode()),
                        1, 0.6D);

        assertTrue(result.found());
        assertEquals(0, result.rejoin().waypointIndex());
        assertEquals(6.0D, result.rejoin().position().x, 1.0E-8D);
        assertEquals(0.0D, result.rejoin().position().z, 1.0E-8D);
    }

    @Test
    void blockedLegRejoinSelectsAnInteriorPointPastTheBlocker() {
        List<SablePathfinder.Waypoint> route = List.of(ground(10.0D, 0.0D));

        SablePathfinder.RouteLegRejoinScan result =
                SablePathfinder.scanRouteLegRejoin(
                        null, route, 0, Vec3.ZERO,
                        new Vec3(0.0D, 0.0D, 2.0D),
                        SablePathfinder.Safety.DEFAULT,
                        query -> query.end().x == 10.0D
                                && query.end().z == 0.0D
                                && query.start().x >= 6.0D
                                ? SablePathfinder.Traversal.clear(query.mode())
                                : SablePathfinder.Traversal.blocked(),
                        1, 0.0D);

        assertTrue(result.found());
        assertEquals(0, result.rejoin().waypointIndex());
        assertTrue(result.rejoin().position().x >= 6.0D);
        assertTrue(result.rejoin().position().x < 6.1D);
        assertEquals(0.0D, result.rejoin().position().z, 1.0E-8D);
        assertTrue(!result.rejoin().directlyReachable());
    }

    @Test
    void trackingTargetCannotRegressBehindCommittedLegProgress() {
        Vec3 target = SablePathfinder.routeLegTrackingTarget(
                Vec3.ZERO, new Vec3(10.0D, 0.0D, 0.0D),
                new Vec3(2.0D, 0.0D, 3.0D),
                SablePathfinder.RouteMode.GROUND, 1.0D, 0.7D);

        assertEquals(8.0D, target.x, 1.0E-8D);
        assertEquals(0.0D, target.z, 1.0E-8D);
    }

    private static SablePathfinder.Waypoint ground(double x, double z) {
        return new SablePathfinder.Waypoint(
                new Vec3(x, 0.0D, z), SablePathfinder.RouteMode.GROUND);
    }
}
