package com.rieno.gadgetsandgizmos.lib.navigation;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SplineConstraintFrameTest{
    private static final Vec3 FORWARD = new Vec3(1.0D, 0.0D, 0.0D);
    private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);
    private static final WaypointSpline SPLINE = WaypointSpline.of(List.of(
            Vec3.ZERO, new Vec3(20.0D, 0.0D, 0.0D)));

    @Test
    void railDriveAcceleratesFromRestAndRetainsCruiseSpeed(){
        Vec3 velocity = Vec3.ZERO;
        for(int idx = 0; idx < 100; idx++){
            velocity = SplineConstraintFrame.driveVelocity(velocity, FORWARD,
                    SplineConstraintFrame.AxisPolicy.HORIZONTAL, 20.0D, 20.0D, 2.5D, 0.01D);
        }
        assertEquals(20.0D, velocity.x, 1.0E-8D);
        assertEquals(20.0D, SplineConstraintFrame.driveVelocity(velocity, FORWARD,
                SplineConstraintFrame.AxisPolicy.HORIZONTAL, 20.0D, 20.0D, 2.5D, 0.01D).x, 1.0E-8D);
    }

    @Test
    void railDrivePreservesGroundSuspensionAndObeysTheLiveBrakingCap(){
        Vec3 velocity = new Vec3(8.0D, -3.0D, 0.4D);
        Vec3 res = SplineConstraintFrame.driveVelocity(velocity, FORWARD,
                SplineConstraintFrame.AxisPolicy.HORIZONTAL, 0.0D, 20.0D, 2.5D, 0.01D);
        assertEquals(7.975D, res.x, 1.0E-8D);
        assertEquals(velocity.y, res.y, 1.0E-8D);
        assertEquals(velocity.z, res.z, 1.0E-8D);
    }

    @Test
    void capturesOnlyAnAlignedVehicleWithLowSidewaysSpeed(){
        Vec3 pos = new Vec3(5.0D, 0.0D, 0.1D);
        assertTrue(capture(pos, new Vec3(5.0D, 0.0D, 0.1D), FORWARD,
                SplineConstraintFrame.AxisPolicy.ALL));
        assertFalse(capture(pos, new Vec3(5.0D, 0.0D, 2.0D), FORWARD,
                SplineConstraintFrame.AxisPolicy.ALL));
        assertFalse(capture(pos, Vec3.ZERO, FORWARD.scale(-1.0D),
                SplineConstraintFrame.AxisPolicy.ALL));
        assertFalse(capture(pos, FORWARD.scale(-1.0D), FORWARD,
                SplineConstraintFrame.AxisPolicy.ALL));
    }

    @Test
    void overlapCertifiedAttachmentChecksPoseWithoutRejectingRecoverableMomentum(){
        Vec3 pos = new Vec3(5.0D, 0.0D, 0.1D);
        WaypointSpline.Projection projection = SPLINE.project(pos);
        assertTrue(SplineConstraintFrame.canAttach(
                pos, FORWARD, UP, projection,
                SplineConstraintFrame.AxisPolicy.HORIZONTAL,
                0.35D, Math.toRadians(20.0D)));
        assertFalse(SplineConstraintFrame.canAttach(
                pos, FORWARD.scale(-1.0D), UP, projection,
                SplineConstraintFrame.AxisPolicy.HORIZONTAL,
                0.35D, Math.toRadians(20.0D)));
        assertFalse(SplineConstraintFrame.canAttach(
                new Vec3(5.0D, 0.0D, 1.0D), FORWARD, UP,
                SPLINE.project(new Vec3(5.0D, 0.0D, 1.0D)),
                SplineConstraintFrame.AxisPolicy.HORIZONTAL,
                0.35D, Math.toRadians(20.0D)));
    }

    @Test
    void groundCaptureLeavesHeightAndTerrainTiltFree(){
        Vec3 pos = new Vec3(5.0D, 8.0D, 0.1D);
        Vec3 facing = new Vec3(1.0D, 0.5D, 0.0D).normalize();
        assertTrue(capture(pos, new Vec3(5.0D, 3.0D, 0.1D), facing,
                SplineConstraintFrame.AxisPolicy.HORIZONTAL));
        assertFalse(capture(pos, Vec3.ZERO, FORWARD, SplineConstraintFrame.AxisPolicy.ALL));
    }

    @Test
    void rejectsAStateOutsideTheCaptureCorridor(){
        assertFalse(capture(new Vec3(5.0D, 0.0D, 1.0D), Vec3.ZERO, FORWARD,
                SplineConstraintFrame.AxisPolicy.HORIZONTAL));
    }

    @Test
    void compliantCaptureUsesCentrelineOverlapBeforeRigidPoseAcceptance(){
        Vec3 position = new Vec3(5.0D, 0.0D, 0.6D);
        Vec3 angled = new Vec3(0.2D, 0.0D, 1.0D).normalize();
        assertTrue(SplineConstraintFrame.canGuide(position, Vec3.ZERO, angled,
                SPLINE.project(position), SplineConstraintFrame.AxisPolicy.HORIZONTAL, 0.75D));
        assertTrue(SplineConstraintFrame.canGuide(position, FORWARD.scale(-1.0D), angled,
                SPLINE.project(position), SplineConstraintFrame.AxisPolicy.HORIZONTAL, 0.75D));
        assertTrue(SplineConstraintFrame.canGuide(position, Vec3.ZERO, FORWARD.scale(-1.0D),
                SPLINE.project(position), SplineConstraintFrame.AxisPolicy.HORIZONTAL, 0.75D));
        Vec3 outside = new Vec3(5.0D, 0.0D, 0.8D);
        assertFalse(SplineConstraintFrame.canGuide(outside, Vec3.ZERO, FORWARD,
                SPLINE.project(outside), SplineConstraintFrame.AxisPolicy.HORIZONTAL, 0.75D));
    }

    @Test
    void hullCaptureUsesPhysicalOverlapInsteadOfTheVehicleCentre(){
        AABB hull = new AABB(4.0D, 4.0D, -1.0D, 6.0D, 6.0D, 1.0D);
        SplineConstraintFrame.HullCapture ground = SplineConstraintFrame.captureHull(
                SPLINE, 0, 0.0D, List.of(hull),
                SplineConstraintFrame.AxisPolicy.HORIZONTAL, 0.0D);
        assertTrue(ground.found());
        assertEquals(0.0D, ground.separation(), 1.0E-8D);
        assertEquals(5.0D, ground.anchor().y, 1.0E-8D);
        assertTrue(ground.projection().position().x > 5.5D);
        assertTrue(SplineConstraintFrame.canCapture(
                ground.anchor(), Vec3.ZERO, FORWARD, UP, ground.projection(),
                SplineConstraintFrame.AxisPolicy.HORIZONTAL,
                0.05D, Math.toRadians(20.0D), 0.75D));
        assertFalse(SplineConstraintFrame.captureHull(
                SPLINE, 0, 0.0D, List.of(hull),
                SplineConstraintFrame.AxisPolicy.ALL, 0.0D).found());
    }

    @Test
    void hullCaptureCanAdvanceAcrossOneAdjacentSplineCorner(){
        WaypointSpline corner = WaypointSpline.of(List.of(
                Vec3.ZERO, new Vec3(10.0D, 0.0D, 0.0D),
                new Vec3(10.0D, 0.0D, 10.0D)));
        AABB hull = new AABB(9.5D, -1.0D, 1.0D, 10.5D, 1.0D, 3.0D);
        SplineConstraintFrame.HullCapture capture = SplineConstraintFrame.captureHull(
                corner, 0, 0.95D, List.of(hull),
                SplineConstraintFrame.AxisPolicy.HORIZONTAL, 0.125D);
        assertTrue(capture.found());
        assertEquals(1, capture.projection().segmentIndex());
    }

    @Test
    void lostCursorCanRecaptureAPhysicalHullAnywhereOnTheRoute(){
        WaypointSpline route = WaypointSpline.of(List.of(
                Vec3.ZERO, new Vec3(10.0D, 0.0D, 0.0D),
                new Vec3(20.0D, 0.0D, 0.0D),
                new Vec3(30.0D, 0.0D, 0.0D)));
        AABB hull = new AABB(24.0D, -1.0D, -1.0D, 26.0D, 1.0D, 1.0D);
        assertFalse(SplineConstraintFrame.captureHull(
                route, 0, 0.0D, List.of(hull),
                SplineConstraintFrame.AxisPolicy.HORIZONTAL, 0.0D).found());
        SplineConstraintFrame.HullCapture capture =
                SplineConstraintFrame.captureHullAnywhere(
                        route, List.of(hull),
                        SplineConstraintFrame.AxisPolicy.HORIZONTAL, 0.0D);
        assertTrue(capture.found());
        assertEquals(2, capture.projection().segmentIndex());
        assertEquals(0.0D, capture.separation(), 1.0E-8D);
    }

    @Test
    void flightCaptureChecksBankAsWellAsHeading(){
        Vec3 pos = new Vec3(5.0D, 0.0D, 0.0D);
        assertFalse(SplineConstraintFrame.canCapture(pos, Vec3.ZERO, FORWARD,
                new Vec3(0.0D, -1.0D, 0.0D), SPLINE.project(pos),
                SplineConstraintFrame.AxisPolicy.ALL, 0.35D, Math.toRadians(20.0D), 0.75D));
    }

    @Test
    void frameMapsControllerForwardAndUpWithoutReversingThem(){
        Vec3 localForward = new Vec3(0.0D, 0.0D, -1.0D);
        Vec3 target = new Vec3(-1.0D, 0.0D, 0.0D);
        Quaterniond local = SplineConstraintFrame.orientation(localForward, UP);
        Quaterniond world = SplineConstraintFrame.orientation(target, UP);
        Quaterniond attitude = new Quaterniond(world).mul(new Quaterniond(local).invert());
        Vector3d facing = attitude.transform(new Vector3d(0.0D, 0.0D, -1.0D));
        Vector3d up = attitude.transform(new Vector3d(0.0D, 1.0D, 0.0D));
        assertEquals(target.x, facing.x, 1.0E-8D);
        assertEquals(target.z, facing.z, 1.0E-8D);
        assertEquals(1.0D, up.y, 1.0E-8D);
    }

    @Test
    void releasesAtTheTerminalHandoffInsteadOfLockingToTheDock(){
        assertTrue(SplineConstraintFrame.beforeHandoff(
                SPLINE, SPLINE.project(new Vec3(5.0D, 0.0D, 0.0D)), 2.0D));
        assertFalse(SplineConstraintFrame.beforeHandoff(
                SPLINE, SPLINE.project(new Vec3(19.0D, 0.0D, 0.0D)), 2.0D));
        assertFalse(SplineConstraintFrame.beforeHandoff(null, null, 2.0D));
    }

    @Test
    void releasesBeforeAHighSpeedPhysicsStepCrossesTheHandoff(){
        WaypointSpline.Projection projection = SPLINE.project(
                new Vec3(17.5D, 0.0D, 0.0D));
        assertTrue(SplineConstraintFrame.beforePredictedHandoff(
                SPLINE, projection, FORWARD.scale(20.0D),
                SplineConstraintFrame.AxisPolicy.HORIZONTAL, 0.01D, 2.0D));
        assertFalse(SplineConstraintFrame.beforePredictedHandoff(
                SPLINE, projection, FORWARD.scale(60.0D),
                SplineConstraintFrame.AxisPolicy.HORIZONTAL, 0.01D, 2.0D));
    }

    @Test
    void transportPreservesCruiseSpeedThroughTurnsAndLeavesGroundHeightVelocityFree(){
        Vec3 prev = new Vec3(0.0D, 0.0D, 1.0D);
        Vec3 next = new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 velocity = new Vec3(0.0D, 3.0D, 20.0D);
        Vec3 res = SplineConstraintFrame.transportVelocity(velocity, prev, next,
                SplineConstraintFrame.AxisPolicy.HORIZONTAL);
        assertEquals(20.0D, res.x, 1.0E-8D);
        assertEquals(3.0D, res.y, 1.0E-8D);
        assertEquals(0.0D, res.z, 1.0E-8D);
        assertEquals(velocity.length(), res.length(), 1.0E-8D);
    }

    @Test
    void flightTransportPreservesSpeedWhileClimbingAndReverseMomentumWhileTurning(){
        Vec3 next = new Vec3(1.0D, 1.0D, 0.0D).normalize();
        Vec3 res = SplineConstraintFrame.transportVelocity(FORWARD.scale(30.0D), FORWARD,
                next, SplineConstraintFrame.AxisPolicy.ALL);
        assertEquals(30.0D, res.length(), 1.0E-8D);
        assertEquals(30.0D, res.dot(next), 1.0E-8D);
        Vec3 reverse = SplineConstraintFrame.transportVelocity(FORWARD.scale(-10.0D),
                FORWARD, next, SplineConstraintFrame.AxisPolicy.ALL);
        assertEquals(-10.0D, reverse.dot(next), 1.0E-8D);
    }

    @Test
    void retainedFrameDampsLateralDriftWithoutAttractingPosition(){
        Vec3 position = new Vec3(5.0D, 4.0D, 0.2D);
        Vec3 velocity = new Vec3(20.0D, -3.0D, 4.0D);
        Vec3 res = SplineConstraintFrame.retainFrameVelocity(
                velocity, position, SPLINE.project(position),
                SplineConstraintFrame.AxisPolicy.HORIZONTAL,
                0.01D, 40.0D);
        assertEquals(20.0D, res.x, 1.0E-8D);
        assertEquals(-3.0D, res.y, 1.0E-8D);
        assertEquals(3.6D, res.z, 1.0E-8D);
        assertTrue(res.z >= 0.0D);
        Vec3 opposite = SplineConstraintFrame.retainFrameVelocity(
                velocity, new Vec3(5.0D, 4.0D, -0.2D), SPLINE.project(position),
                SplineConstraintFrame.AxisPolicy.HORIZONTAL,
                0.01D, 40.0D);
        assertEquals(res, opposite);
    }

    private static boolean capture(Vec3 pos, Vec3 velocity, Vec3 forward,
                                   SplineConstraintFrame.AxisPolicy axes){
        return SplineConstraintFrame.canCapture(pos, velocity, forward, UP, SPLINE.project(pos),
                axes, 0.35D, Math.toRadians(20.0D), 0.75D);
    }
}
