package com.rieno.gadgetsandgizmos.lib.navigation;

import com.rieno.gadgetsandgizmos.lib.scm.ScmControlMode;
import com.rieno.gadgetsandgizmos.lib.scm.ScmControlModeRegistry;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointSplineTest {
    @Test
    void twoPointSplineRemainsStraight() {
        WaypointSpline spline = WaypointSpline.of(List.of(
                new Vec3(0.0D, 0.0D, 0.0D),
                new Vec3(10.0D, 0.0D, 0.0D)));

        assertFalse(spline.isEmpty());
        assertEquals(5.0D, spline.segments().getFirst()
                .pointAtFraction(0.5D).x, 1.0E-6D);
        assertEquals(0.0D, spline.segments().getFirst()
                .pointAtFraction(0.5D).z, 1.0E-6D);
    }

    @Test
    void cornerRetainsWaypointAndContinuousTangent() {
        WaypointSpline spline = WaypointSpline.of(List.of(
                new Vec3(0.0D, 0.0D, 0.0D),
                new Vec3(10.0D, 0.0D, 0.0D),
                new Vec3(10.0D, 0.0D, 10.0D)));

        assertEquals(new Vec3(10.0D, 0.0D, 0.0D),
                spline.segments().getFirst().pointAtFraction(1.0D));
        Vec3 incoming = spline.segments().getFirst().tangentAtFraction(1.0D);
        Vec3 outgoing = spline.segments().getLast().tangentAtFraction(0.0D);
        assertTrue(incoming.dot(outgoing) > 0.999D);
    }

    @Test
    void orderedProjectionHonorsCommittedFraction() {
        WaypointSpline spline = WaypointSpline.of(List.of(
                new Vec3(0.0D, 0.0D, 0.0D),
                new Vec3(10.0D, 0.0D, 0.0D),
                new Vec3(20.0D, 0.0D, 0.0D)));

        WaypointSpline.Projection projection = spline.project(
                new Vec3(2.0D, 0.0D, 0.0D), 0, 0.6D);

        assertTrue(projection.found());
        assertTrue(projection.fraction() >= 0.6D);
    }

    @Test
    void repeatedOrderedProjectionDoesNotAdvanceAStationaryBody() {
        WaypointSpline spline = WaypointSpline.of(List.of(
                Vec3.ZERO, new Vec3(20.0D, 0.0D, 0.0D)));
        Vec3 position = new Vec3(5.0D, 0.0D, 0.5D);
        WaypointSpline.Projection projection = spline.project(position);
        double initial = projection.fraction();

        for (int idx = 0; idx < 1_000; idx++) {
            projection = spline.projectSegment(position, 0, projection.fraction());
        }

        assertEquals(initial, projection.fraction(), 1.0E-12D);
        assertEquals(5.0D, projection.position().x, 1.0E-3D);
    }

    @Test
    void segmentProjectionCannotJumpAcrossLaterCrossing() {
        WaypointSpline spline = WaypointSpline.of(List.of(
                new Vec3(-10.0D, 0.0D, 0.0D),
                new Vec3(10.0D, 0.0D, 0.0D),
                new Vec3(0.0D, 0.0D, -10.0D),
                new Vec3(0.0D, 0.0D, 10.0D)));

        WaypointSpline.Projection projection = spline.projectSegment(
                new Vec3(0.0D, 0.0D, 0.1D), 0, 0.0D);
        WaypointSpline.TrackingTarget target = spline.trackingTarget(projection, 2.0D);

        assertTrue(projection.found());
        assertEquals(0, projection.segmentIndex());
        assertTrue(target.found());
        assertTrue(target.distanceAlongRoute() > projection.distanceAlongRoute());
    }

    @Test
    void raycastFindsCurveAndAuthoredWaypoint() {
        WaypointSpline spline = WaypointSpline.of(List.of(
                new Vec3(0.0D, 0.0D, 0.0D),
                new Vec3(5.0D, 0.0D, 0.0D),
                new Vec3(10.0D, 0.0D, 5.0D)));

        WaypointSpline.RayHit curve = spline.raycast(
                new Vec3(5.0D, 4.0D, 0.0D), new Vec3(0.0D, -1.0D, 0.0D),
                8.0D, 0.3D, 0.25D);
        WaypointSpline.WaypointHit waypoint = spline.raycastWaypoint(
                new Vec3(5.0D, 4.0D, 0.0D), new Vec3(0.0D, -1.0D, 0.0D),
                8.0D, 0.3D);

        assertTrue(curve.found());
        assertTrue(waypoint.found());
        assertEquals(1, waypoint.waypointIndex());
    }

    @Test
    void boundedSamplingRetainsEverySegmentBoundary() {
        List<Vec3> controls = List.of(
                Vec3.ZERO,
                new Vec3(100.0D, 0.0D, 0.0D),
                new Vec3(100.0D, 100.0D, 0.0D));
        List<Vec3> samples = WaypointSpline.of(controls).sample(0.01D, 12);

        assertTrue(samples.size() <= 12);
        assertEquals(controls.getFirst(), samples.getFirst());
        assertTrue(samples.contains(controls.get(1)));
        assertEquals(controls.getLast(), samples.getLast());
    }

    @Test
    void immutableWaypointEditsPreserveRouteEndpoints() {
        WaypointSpline spline = WaypointSpline.of(List.of(
                Vec3.ZERO, new Vec3(10.0D, 0.0D, 0.0D)));
        WaypointSpline added = spline.withWaypointAdded(
                0, new Vec3(5.0D, 2.0D, 0.0D));
        WaypointSpline moved = added.withWaypointMoved(
                1, new Vec3(5.0D, 3.0D, 0.0D));
        WaypointSpline removed = moved.withWaypointRemoved(1);

        assertEquals(2, spline.waypoints().size());
        assertEquals(new Vec3(5.0D, 3.0D, 0.0D), moved.waypoints().get(1));
        assertEquals(spline.waypoints(), removed.waypoints());
    }

    @Test
    void groundControlJoinsAlongTheSplineInsteadOfAcrossIt() {
        WaypointSpline spline = WaypointSpline.of(List.of(
                Vec3.ZERO, new Vec3(100.0D, 0.0D, 0.0D)));
        Vec3 vehicle = new Vec3(10.0D, 0.0D, 20.0D);
        GroundPathPlanner.ForwardRouteControl control = GroundPathPlanner.forwardSplineControl(
                spline, spline.project(vehicle), vehicle,
                new GroundPathPlanner.VehicleCapabilities(
                        3.0D, Math.toRadians(30.0D), 0.0D, 0.0D, 0.0D, true),
                3.0D, 8.0D, 1.0D, 2.5D, 0.35D, 0.35D);

        assertTrue(control.steeringDirection().x > 0.8D);
        assertTrue(control.steeringDirection().z < 0.0D);
    }

    @Test
    void groundControlFeedsTheSignedCurveIntoSteeringWhileCentered() {
        GroundPathPlanner.VehicleCapabilities vehicle =
                new GroundPathPlanner.VehicleCapabilities(
                        2.0D, Math.toRadians(30.0D),
                        0.0D, 0.0D, 0.0D, true);
        WaypointSpline rightCurve = WaypointSpline.of(List.of(
                Vec3.ZERO, new Vec3(10.0D, 0.0D, 0.0D),
                new Vec3(10.0D, 0.0D, 10.0D)));
        WaypointSpline leftCurve = WaypointSpline.of(List.of(
                Vec3.ZERO, new Vec3(10.0D, 0.0D, 0.0D),
                new Vec3(10.0D, 0.0D, -10.0D)));
        Vec3 centered = new Vec3(5.0D, 0.0D, 0.0D);

        GroundPathPlanner.ForwardRouteControl right = GroundPathPlanner.forwardSplineControl(
                rightCurve, rightCurve.projectSegment(centered, 0, 0.0D), centered,
                vehicle, 3.0D, 8.0D, 1.0D, 2.5D, 0.35D, 0.35D);
        GroundPathPlanner.ForwardRouteControl left = GroundPathPlanner.forwardSplineControl(
                leftCurve, leftCurve.projectSegment(centered, 0, 0.0D), centered,
                vehicle, 3.0D, 8.0D, 1.0D, 2.5D, 0.35D, 0.35D);

        assertTrue(right.signedCurvature() < 0.0D);
        assertTrue(right.steeringFeedForward() < 0.0D);
        assertTrue(left.signedCurvature() > 0.0D);
        assertTrue(left.steeringFeedForward() > 0.0D);
    }

    @Test
    void curvatureFeedForwardKeepsABicycleModelOnTheSpline() {
        WaypointSpline spline = WaypointSpline.of(List.of(
                Vec3.ZERO, new Vec3(20.0D, 0.0D, 0.0D),
                new Vec3(20.0D, 0.0D, 20.0D)));
        GroundPathPlanner.VehicleCapabilities vehicle =
                new GroundPathPlanner.VehicleCapabilities(
                        2.0D, Math.toRadians(30.0D),
                        0.0D, 0.0D, 0.0D, true);
        ScmControlMode car = ScmControlModeRegistry.resolve("car");
        Vec3 position = Vec3.ZERO;
        Vec3 heading = new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        double speed = 3.0D;
        double yawRate = 0.0D;
        int segment = 0;
        double fraction = 0.0D;
        double maximumError = 0.0D;
        WaypointSpline.Projection projection = spline.projectSegment(
                position, segment, fraction);
        for (int tick = 0; tick < 260; tick++) {
            projection = spline.project(position, segment, fraction);
            segment = projection.segmentIndex();
            fraction = projection.fraction();
            GroundPathPlanner.ForwardRouteControl control =
                    GroundPathPlanner.forwardSplineControl(
                            spline, projection, position, vehicle,
                            3.0D, speed, 1.0D, 2.5D,
                            0.35D, 0.35D);
            if (projection.distance() > maximumError) {
                maximumError = projection.distance();
            }
            ScmControlMode.ControlOutput output = car.navigate(
                    new ScmControlMode.ControlInput(
                            position, heading.scale(speed), up.scale(yawRate),
                            heading, up, heading.cross(up), control.corner(),
                            control.steeringDirection(), Vec3.ZERO,
                            speed, 0.25D, 0.6D, true,
                            100.0D, 100.0D, speed, -1.0D,
                            true, true, false,
                            control.signedCurvature(), control.steeringFeedForward()));
            double wheelAngle = output.torque().dot(up)
                    * vehicle.maximumSteeringRadians();
            yawRate = speed / vehicle.wheelbase() * Math.tan(wheelAngle);
            heading = rotateYaw(heading, yawRate * 0.05D);
            position = position.add(heading.scale(speed * 0.05D));
        }

        assertTrue(projection.distanceAlongRoute() > spline.length() * 0.75D);
        assertTrue(maximumError < 0.65D);
    }

    private static Vec3 rotateYaw(Vec3 direction, double radians) {
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        return new Vec3(
                direction.x * cosine + direction.z * sine,
                0.0D,
                -direction.x * sine + direction.z * cosine).normalize();
    }
}
