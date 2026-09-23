package com.rieno.gadgetsandgizmos.lib.navigation;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RouteObstacleScanTest {
    @Test
    void scansBoundedSegmentsAndReportsThePhysicalHit(){
        RouteObstacleScan scan = new RouteObstacleScan(List.of(Vec3.ZERO,
                new Vec3(5, 0, 0), new Vec3(10, 0, 0)));
        assertNull(scan.advance((start, end) -> start.x < 1 ? start.distanceTo(end) : 2.0D, 1, Long.MAX_VALUE));
        RouteObstacleScan.Hit hit = scan.advance(
                (start, end) -> start.x < 1 ? start.distanceTo(end) : 2.0D, 1, Long.MAX_VALUE);
        assertNotNull(hit);
        assertEquals(7.0D, hit.position().x, 1.0E-8D);
        assertEquals(1, scan.sweep());
    }

    @Test
    void staggeredQueueSelectsOneFairJobPerInterval(){
        StaggeredWorkQueue<String> queue = new StaggeredWorkQueue<>(20);
        assertEquals("a", queue.next(List.of("a", "b"), 0));
        assertNull(queue.next(List.of("a", "b"), 19));
        assertEquals("b", queue.next(List.of("a", "b"), 20));
        assertEquals("a", queue.next(List.of("a", "b"), 40));
    }

    @Test
    void editableLegHasDockHandlesAndOneMidpoint(){
        List<Vec3> points = SplineRouteGeometry.editableLeg(Vec3.ZERO, new Vec3(20, 0, 0), 4.0D);
        assertEquals(List.of(0.0D, 4.0D, 10.0D, 16.0D, 20.0D),
                points.stream().map(point -> point.x).toList());
    }

    @Test
    void routeHazardSamplesExcludeBothDockContactZones(){
        WaypointSpline spline = WaypointSpline.of(List.of(Vec3.ZERO, new Vec3(20, 0, 0)));
        List<Vec3> points = SplineRouteGeometry.transitInterior(spline, 2.0D, 32, 4.0D);
        assertEquals(4.0D, points.getFirst().x, 0.05D);
        assertEquals(16.0D, points.getLast().x, 0.05D);
    }

    @Test
    void detourMergeStaysAheadOfTheBlockerAndBeforeHandoff(){
        WaypointSpline spline = WaypointSpline.of(List.of(Vec3.ZERO, new Vec3(30, 0, 0)));
        WaypointSpline.Projection vehicle = spline.project(new Vec3(5, 0, 0));
        WaypointSpline.Projection blocker = spline.project(new Vec3(10, 0, 0));
        WaypointSpline.TrackingTarget merge = SplineRouteGeometry.detourMergeTarget(
                spline, vehicle, blocker, 4.0D, 3.0D, 5.0D);
        assertTrue(merge.found());
        assertEquals(14.0D, merge.position().x, 0.1D);
        assertFalse(SplineRouteGeometry.detourMergeTarget(
                spline, vehicle, spline.project(new Vec3(28, 0, 0)),
                4.0D, 3.0D, 5.0D).found());
    }

    @Test
    void detourSpliceRetainsCompletedPrefixAndUntouchedSuffix(){
        List<Vec3> retained = List.of(new Vec3(2, 0, 0), new Vec3(4, 0, 0),
                new Vec3(6, 0, 0), new Vec3(8, 0, 0), new Vec3(10, 0, 0));
        SplineRouteGeometry.RouteSplice splice = SplineRouteGeometry.spliceDetour(
                retained, 1, List.of(new Vec3(3, 0, 1), new Vec3(7, 0, 1)), 3);
        assertEquals(List.of(2.0D, 3.0D, 7.0D, 8.0D, 10.0D),
                splice.waypoints().stream().map(point -> point.x).toList());
        assertEquals(1, splice.waypointIndex());
        assertEquals(3, splice.resumeWaypointIndex());
    }
}
