package com.rieno.gadgetsandgizmos.lib.navigation;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SplineRouteGeometryTest{
    private static final WaypointSpline CURVE = WaypointSpline.of(List.of(Vec3.ZERO,
            new Vec3(10.0D, 0.0D, 0.0D), new Vec3(10.0D, 0.0D, 20.0D)));
    private static final GroundPathPlanner.VehicleCapabilities VEHICLE =
            new GroundPathPlanner.VehicleCapabilities(2.0D, Math.toRadians(32.0D),
                    0.0D, 0.0D, 0.0D, true);

    @Test
    void rejoinTargetsAheadOnTheCurveAndRetainsItsOrderedTangent(){
        Vec3 pos = new Vec3(6.0D, 0.0D, -3.0D);
        var projection = CURVE.projectSegment(pos, 0, 0.0D);
        var target = SplineRouteGeometry.mergeTarget(CURVE, projection, pos, 2.0D, 1.0D,
                SplineConstraintFrame.AxisPolicy.HORIZONTAL);
        assertTrue(target.distanceAlongRoute() - projection.distanceAlongRoute() >= 5.0D - 1.0E-8D);
        var point = CURVE.pointAtDistance(target.distanceAlongRoute());
        assertEquals(point.position(), target.position());
        assertEquals(point.tangent(), target.tangent());
    }

    @Test
    void steeringAxleSeesTheBendBeforeTheHullAndDoesNotCommitHullProgress(){
        var center = CURVE.projectSegment(new Vec3(6.0D, 0.0D, 0.0D), 0, 0.0D);
        double progress = center.distanceAlongRoute();
        Vec3 axle = new Vec3(9.0D, 0.0D, 0.0D);
        var projection = SplineRouteGeometry.steeringProjection(CURVE, center, axle, 5.0D);
        assertTrue(projection.distanceAlongRoute() > progress);
        assertEquals(progress, center.distanceAlongRoute());
        var control = control(CURVE, projection, axle);
        assertTrue(Math.abs(control.steeringFeedForward()) > 0.3D);
    }

    @Test
    void smallStraightLineOffsetsDoNotRequestFullSteering(){
        var straight = WaypointSpline.of(List.of(Vec3.ZERO, new Vec3(40.0D, 0.0D, 0.0D)));
        Vec3 pos = new Vec3(10.0D, 0.0D, 0.1D);
        var control = control(straight, straight.project(pos), pos);
        double angle = Math.atan2(control.steeringDirection().z, control.steeringDirection().x);
        assertTrue(Math.abs(angle) < Math.toRadians(3.0D));
        assertTrue(angle < 0.0D);
        assertEquals(0.0D, control.steeringFeedForward(), 1.0E-8D);
    }

    @Test
    void distanceLookupPreservesOrderedLegsAndEndpointsOnLongRoutes(){
        var points = new java.util.ArrayList<Vec3>();
        for(int idx = 0; idx <= 2048; idx++) points.add(new Vec3(idx, 0.0D, 0.0D));
        var spline = WaypointSpline.of(points);
        assertEquals(0, spline.pointAtDistance(0.0D).segmentIndex());
        assertEquals(0, spline.pointAtDistance(1.0D).segmentIndex());
        assertEquals(1700, spline.pointAtDistance(1700.25D).segmentIndex());
        assertEquals(1700.25D, spline.pointAtDistance(1700.25D).position().x, 1.0E-4D);
        assertEquals(points.getLast(), spline.pointAtDistance(spline.length()).position());
    }

    private static GroundPathPlanner.ForwardRouteControl control(WaypointSpline spline,
            WaypointSpline.Projection projection, Vec3 pos){
        return GroundPathPlanner.forwardSplineControl(spline, projection, pos,
                VEHICLE, 4.0D, 10.0D, 2.0D, 2.5D, 0.35D, 1.0D);
    }
}
