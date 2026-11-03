package com.rieno.gadgetsandgizmos.lib.navigation;

import net.minecraft.nbt.TagParser;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// Replay authored lane geometry through the ordered connector cursor
class DockingLaneFollowingTest{
    // A ship displaced above a lane still has physical room to rejoin before a blocked section
    @Test
    void displacedLaneBrakesFromItsPhysicalPosition(){
        var spline = WaypointSpline.of(List.of(Vec3.ZERO, new Vec3(20, 0, 0), new Vec3(40, 0, 0)));
        var route = new SplineRouteTraversal(spline, false);
        route.update(Vec3.ZERO, 1.25D, 0.5D);
        Vec3 pos = new Vec3(0, 50, 0);
        var hull = List.of(new AABB(-2, -2, -2, 2, 2, 2));
        var obstacle = new DockTrafficGeometry.Obstacle(UUID.randomUUID(), List.of(new AABB(-4, -4, -4, 4, 4, 4)));
        double clearance = DockTrafficGeometry.clearance(route.remainingControls(pos), 24, hull, List.of(obstacle));
        assertEquals(24, clearance, 1.0E-6D, "A blocked projection stopped a ship fifty blocks away from the lane");
        assertTrue(DockTrafficGeometry.clearance(route.remainingControls(new Vec3(0, 5, 0)), 24, hull, List.of(obstacle)) <= 0.25D,
                "A ship rejoining inside the actual obstacle was allowed to continue");
        assertEquals(0, route.segment(), "Rejoining skipped the retained directed leg");
    }

    // A sampled curve can enter its stopping radius several short segments before its end
    @Test
    void recordedCurveArrivesBeforeTheLastSampledSegment() throws Exception{
        try(var input = getClass().getResourceAsStream("/navigation/curved-lane-arrival.snbt")){
            assertNotNull(input);
            var tag = TagParser.parseTag(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            var route = SplineRouteTraversal.fromTag(tag).orElseThrow();
            Vec3 pos = WaypointSplineData.vector(tag.getCompound("Position"));
            route.update(pos, 1.25D, 0.5D);
            assertFalse(route.finalLeg());
            assertEquals(1.05656D, route.remainingDistance(pos), 0.001D);
            assertTrue(route.arrived(pos, 1.25D));
            assertFalse(route.arrived(pos, 0.75D));
        }
    }

    // Physical proximity to the endpoint cannot complete an unvisited loop
    @Test
    void arrivalKeepsOrderedTravelAtACrossing(){
        var spline = WaypointSpline.of(List.of(Vec3.ZERO, new Vec3(40, 0, 0), new Vec3(40, 20, 40), Vec3.ZERO));
        var route = new SplineRouteTraversal(spline, true);
        assertFalse(route.arrived(Vec3.ZERO, 1.25D));
        route.update(Vec3.ZERO, 1.25D, 0.5D);
        assertFalse(route.arrived(Vec3.ZERO, 1.25D));
        for(var pos : spline.sample(0.25D)) route.update(pos, 1.25D, 0.5D);
        assertTrue(route.arrived(Vec3.ZERO, 1.25D));
    }

    // A forecast departure ceases to divert an uncaptured ship once its movement clears
    @Test
    void uncapturedApproachReconsidersClearedTraffic(){
        var path = lanes();
        Vec3 facing = new Vec3(0, 0, 1), up = new Vec3(0, 1, 0), pos = new Vec3(90, 0, 10);
        var hull = List.of(new AABB(-1, -1, -1, 1, 1, 1));
        var movement = new DockTrafficGeometry.Movement(UUID.randomUUID(), List.of(new Vec3(25, 0, 10), new Vec3(25, 0, 0)), hull);
        var traffic = new DockTrafficGeometry.Traffic(List.of(), List.of(movement));
        var selected = DockTrafficGeometry.assess(path, Vec3.ZERO, facing, up, pos, hull, List.of(), null, false, traffic);
        var retained = new SplineRouteTraversal(WaypointSpline.of(selected.controls()), true);
        assertTrue(retained.spline().waypoints().stream().anyMatch(point -> point.z > 18));
        var next = DockTrafficGeometry.refreshApproach(path, Vec3.ZERO, facing, up, retained, pos, hull, List.of());
        assertEquals(DockTrafficGeometry.assess(path, Vec3.ZERO, facing, up, pos, hull, List.of()).controls(), next.spline().waypoints());
    }

    // Provisioning a bypass does not commit a ship which has not entered the lane
    @Test
    void uncapturedApproachReturnsToThePrimaryWhenDepartureClears(){
        var path = lanes();
        Vec3 facing = new Vec3(0, 0, 1), up = new Vec3(0, 1, 0), pos = new Vec3(90, 0, 10);
        var hull = List.of(new AABB(-1, -1, -1, 1, 1, 1));
        var obstacle = new DockTrafficGeometry.Obstacle(UUID.randomUUID(), List.of(new AABB(20, -1, 1, 32, 6, 11)));
        var selected = DockTrafficGeometry.assess(path, Vec3.ZERO, facing, up, pos, hull, List.of(obstacle));
        var retained = new SplineRouteTraversal(WaypointSpline.of(selected.controls()), true);
        assertTrue(retained.spline().waypoints().stream().anyMatch(point -> point.z > 18));
        var next = DockTrafficGeometry.refreshApproach(path, Vec3.ZERO, facing, up, retained, pos, hull, List.of());
        var primary = DockTrafficGeometry.assess(path, Vec3.ZERO, facing, up, pos, hull, List.of());
        assertEquals(primary.controls(), next.spline().waypoints());
        assertFalse(next.captured());
    }

    // Captured travel cannot cross into a primary lane which has since become available
    @Test
    void capturedApproachRetainsItsClearBypass(){
        var path = lanes();
        Vec3 facing = new Vec3(0, 0, 1), up = new Vec3(0, 1, 0), pos = new Vec3(90, 0, 10);
        var hull = List.of(new AABB(-1, -1, -1, 1, 1, 1));
        var obstacle = new DockTrafficGeometry.Obstacle(UUID.randomUUID(), List.of(new AABB(20, -1, 1, 32, 6, 11)));
        var selected = DockTrafficGeometry.assess(path, Vec3.ZERO, facing, up, pos, hull, List.of(obstacle));
        var retained = new SplineRouteTraversal(WaypointSpline.of(selected.controls()), false);
        assertSame(retained, DockTrafficGeometry.refreshApproach(path, Vec3.ZERO, facing, up, retained,
                retained.entry(), hull, List.of()));
    }

    @Test
    void horizontalLaneGuidanceReachesItsDockingCrossing() throws Exception{
        try(var input = getClass().getResourceAsStream("/navigation/horizontal-docking-approach.snbt")){
            assertNotNull(input);
            var path = DockingApproachPath.fromTag(TagParser.parseTag(new String(input.readAllBytes(), StandardCharsets.UTF_8)));
            var routes = path.routes(Vec3.ZERO, new Vec3(0, 0, 1), new Vec3(0, 1, 0), false);
            assertFalse(routes.isEmpty());
            for(var candidate : routes){
                var route = new SplineRouteTraversal(WaypointSpline.of(candidate.controls()), true);
                Vec3 pos = route.entry();
                Vec3 end = candidate.controls().getLast();
                for(int tick = 0; tick < 10000 && (pos.distanceTo(end) > 0.1D || !route.finalLeg()); tick++){
                    var target = route.update(pos, 1.25D, 0.5D);
                    assertTrue(target.found());
                    Vec3 delta = target.position().subtract(pos);
                    pos = pos.add(delta.normalize().scale(Math.min(0.1D, delta.length())));
                }
                assertTrue(route.finalLeg() && pos.distanceTo(end) <= 0.1D,
                        "Connector stopped at " + pos + " before " + end + "; cursor=" + route.segment() + ":" + route.fraction());
            }
        }
    }

    private static DockingApproachPath lanes(){
        Vec3 facing = new Vec3(0, 0, 1), up = new Vec3(0, 1, 0);
        var axes = List.of(new DockingApproachAxis(Vec3.ZERO, facing, 64),
                new DockingApproachAxis(new Vec3(26, 0, 0), facing, 64),
                new DockingApproachAxis(new Vec3(52, 0, 0), facing, 64));
        return DockingApproachPath.captureLanes(Vec3.ZERO, facing, up, new Vec3(0, 0, 64), DockingApproachLanes.defaults(axes, up));
    }
}
