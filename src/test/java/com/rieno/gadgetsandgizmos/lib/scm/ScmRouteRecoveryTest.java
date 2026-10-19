package com.rieno.gadgetsandgizmos.lib.scm;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.navigation.GroundPathPlanner;
import com.rieno.gadgetsandgizmos.lib.navigation.SablePathfinder;
import com.rieno.gadgetsandgizmos.lib.navigation.SplineConstraintFrame;
import com.rieno.gadgetsandgizmos.lib.navigation.WaypointSpline;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Exercise route recovery through a moving, quantized bicycle drivetrain
class ScmRouteRecoveryTest{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Capture the route from both sides and from sideways or opposing headings
    @Test void recoversGroundVehiclesOntoTheRoute(){
        for(double offset : new double[]{-8.0D, 8.0D}){
            for(double degrees : new double[]{0.0D, 90.0D, -90.0D, 175.0D, -175.0D, 180.0D}){
                simulateRecovery(offset, degrees);
            }
        }
    }

    // A small sensor change at the opposing heading must not reverse the committed turn
    @Test void retainsTurnAcrossOpposingHeadingNoise(){
        ScmGroundSteering steering = new ScmGroundSteering();
        for(int idx = 0; idx < 35; idx++){
            double error = Math.toRadians(idx % 2 == 0 ? 179.9D : -179.9D);
            assertTrue(steering.update(error, 0.0D, 1.0D, 0.0D, 0.0D, 0.05D) > 0.0D);
        }
        double val = 0.0D;
        for(int idx = 0; idx < 30; idx++) val = steering.update(-0.1D, 0.0D, 1.0D, 0.0D, 0.0D, 0.05D);
        assertTrue(val < 0.0D);
    }

    // Recovery must reach full lock through bounded intermediate inputs
    @Test void reachesFullLockIncrementally(){
        ScmGroundSteering steering = new ScmGroundSteering();
        double prev = 0.0D;
        for(int idx = 0; idx < 20; idx++){
            double next = steering.update(Math.PI * 0.5D, 0.0D, 0.0D, 0.0D, 0.0D, 0.05D);
            assertTrue(next >= prev);
            assertTrue(next - prev <= 0.08000001D);
            prev = next;
        }
        assertEquals(1.0D, prev, 1.0E-9D);
    }

    // Straight or nearly-opposing pure pursuit must retain heading correction
    @Test void combinesHeadingAndCurvatureFeedback(){
        assertEquals(1.0D, ScmControlAxes.routeSteeringDemand(Math.PI, 0, 3, 0, 0));
        assertTrue(ScmControlAxes.routeSteeringDemand(0.03D, 0, 3, 0, 0) > 0.0D);
        assertEquals(0.4D, ScmControlAxes.routeSteeringDemand(0, 0.1D, 3, 0.3D, 0.4D), 1.0E-9D);
    }

    // Do not turn tiny corrections into a nonzero minimum wheel angle
    @Test void preservesNeutralWhenQuantizingSteering(){
        assertEquals(0, ScmControlAxes.steeringSignal(0.02D, 15));
        assertEquals(0, ScmControlAxes.steeringSignal(-0.02D, 15));
        assertEquals(8, ScmControlAxes.steeringSignal(0.5D, 15));
        assertEquals(-8, ScmControlAxes.steeringSignal(-0.5D, 15));
        assertEquals(15, ScmControlAxes.steeringSignal(1.0D, 15));
        assertEquals(0, ScmControlAxes.steeringSignal(Double.NaN, 15));
    }

    // A mass change must not make the only steering axle disappear behind the new centre
    @Test void retainsSingleAxleAuthorityAcrossMassChanges(){
        assertEquals(1.0D, ScmSteeringMode.FRONT_WHEEL.wheelDemand(1, -4, -4, -4));
        assertEquals(-1.0D, ScmSteeringMode.REAR_WHEEL.wheelDemand(1, 4, 4, 4));
        assertEquals(0.0D, ScmSteeringMode.FRONT_WHEEL.wheelDemand(1, -4, -4, 4));
    }

    // A nearby route must not pull a sideways hull before its tyres align
    @Test void waitsForHeadingAndLateralMotionBeforeGuiding(){
        WaypointSpline spline = straightRoute();
        Vec3 pos = new Vec3(1, 0, 10);
        var projection = spline.project(pos, 0);
        var axes = SplineConstraintFrame.AxisPolicy.HORIZONTAL;
        assertFalse(SplineConstraintFrame.canGuide(pos, Vec3.ZERO, new Vec3(1, 0, 0),
                projection, axes, 1.5D, Math.toRadians(22.5D), 1.0D));
        assertFalse(SplineConstraintFrame.canGuide(pos, new Vec3(2, 0, 0), new Vec3(0, 0, 1),
                projection, axes, 1.5D, Math.toRadians(22.5D), 1.0D));
        assertTrue(SplineConstraintFrame.canGuide(pos, new Vec3(0, 0, 2), new Vec3(0, 0, 1),
                projection, axes, 1.5D, Math.toRadians(22.5D), 1.0D));
    }

    // Projection onto a distant endpoint must not advance the retained route cursor
    @Test void requiresPhysicalCaptureToAdvanceSplineProgress(){
        WaypointSpline spline = WaypointSpline.of(List.of(Vec3.ZERO, new Vec3(0, 0, 10), new Vec3(0, 0, 20)));
        Vec3 far = new Vec3(30, 0, 30);
        var projection = spline.projectSegment(far, 0, 0.0D);
        assertFalse(SplineConstraintFrame.canAdvance(far, projection,
                SplineConstraintFrame.AxisPolicy.HORIZONTAL, 1.0D, 0.97D));
        Vec3 captured = new Vec3(0.1D, 4, 10);
        projection = spline.projectSegment(captured, 0, 0.0D);
        assertTrue(SplineConstraintFrame.canAdvance(captured, projection,
                SplineConstraintFrame.AxisPolicy.HORIZONTAL, 1.0D, 0.97D));
        assertFalse(SplineConstraintFrame.canAdvance(captured, projection,
                SplineConstraintFrame.AxisPolicy.ALL, 1.0D, 0.97D));
    }

    // Terrain height must not dilute a horizontal steering request
    @Test void ignoresVerticalOffsetInGroundLookahead(){
        WaypointSpline spline = straightRoute();
        var vehicle = capabilities();
        Vec3 low = new Vec3(3, 0, 10);
        Vec3 high = new Vec3(3, 80, 10);
        var first = control(spline, low, new Vec3(0, 0, 1), vehicle);
        var second = control(spline, high, new Vec3(0, 0, 1), vehicle);
        assertEquals(first.steeringFeedForward(), second.steeringFeedForward(), 1.0E-8D);
    }

    // Calibrated yaw poses must cover neutral, partial input and both full locks
    @Test void interpolatesCalibratedSteeringBearings(){
        var poses = List.of(new ScmPoseControl.SteeringPose(-30, 0, 80),
                new ScmPoseControl.SteeringPose(0, 0, 10), new ScmPoseControl.SteeringPose(30, 0, -130));
        assertEquals(0, ScmPoseControl.steeringTarget(poses, 0).angleX());
        assertEquals(-15, ScmPoseControl.steeringTarget(poses, 0.5D).angleX(), 1.0E-9D);
        assertEquals(30, ScmPoseControl.steeringTarget(poses, -1).angleX());
        assertEquals(-30, ScmPoseControl.steeringTarget(poses, 1).angleX());
    }

    // Connected bodies and an authored dock heading must not remove ground transit steering
    @Test void retainsYawWhileGroundVehiclesRecover(){
        assertTrue(ScmControlPriority.navigationOwnsYaw(true, false, false, true));
        assertTrue(ScmControlPriority.navigationOwnsYaw(true, true, false, true));
        assertFalse(ScmControlPriority.navigationOwnsYaw(true, true, true, true));
        assertFalse(ScmControlPriority.navigationOwnsYaw(false, false, false, true));
        assertFalse(ScmControlPriority.navigationOwnsYaw(false, true, false, false));
    }

    // Live straight route legs need the same recovery when no spline curvature is available
    @Test void recoversOntoLiveRouteLegs(){
        Vec3 pos = new Vec3(8, 0, 30);
        Vec3 end = new Vec3(0, 0, 800);
        double yaw = Math.toRadians(175);
        double rate = 0.0D;
        ScmGroundSteering steering = new ScmGroundSteering();
        for(int idx = 0; idx < 1600; idx++){
            Vec3 forward = new Vec3(Math.sin(yaw), 0, Math.cos(yaw));
            Vec3 target = SablePathfinder.routeLegTrackingTarget(Vec3.ZERO, end, pos,
                    SablePathfinder.RouteMode.GROUND, 8, 0);
            var route = GroundPathPlanner.forwardRouteControl(pos, target, Vec3.ZERO, List.of(end), 0,
                    capabilities(), 3, 2, 2, 0.75D, 0.5D);
            double error = Math.atan2(forward.cross(route.steeringDirection()).y,
                    forward.dot(route.steeringDirection()));
            double input = steering.update(error, 0, 3, rate, 0, 0.05D);
            rate = 3 * Math.tan(ScmControlAxes.steeringSignal(input, 15) / 15.0D * Math.toRadians(30)) / 4;
            yaw += rate * 0.05D;
            pos = pos.add(forward.scale(0.15D));
        }
        assertTrue(Math.abs(pos.x) < 0.35D, "lateral error=" + pos.x);
        assertTrue(Math.cos(yaw) > Math.cos(Math.toRadians(5)));
    }

    // Preserve turn anticipation and braking when normal curve tracking resumes
    @Test void retainsCurvedRouteSteeringAndSpeedLimits(){
        WaypointSpline spline = WaypointSpline.of(List.of(Vec3.ZERO, new Vec3(0, 0, 20),
                new Vec3(20, 0, 40), new Vec3(40, 0, 40)));
        Vec3 pos = new Vec3(0, 0, 12);
        var route = GroundPathPlanner.forwardSplineControl(spline, spline.project(pos, 0), pos,
                new Vec3(0, 0, 1), capabilities(), 4, 12, 2, 2, 0.75D, 0.5D);
        assertTrue(route.steeringFeedForward() > 0);
        assertTrue(route.permittedSpeed() < 12);
        assertTrue(route.permittedSpeed() > 0);
        assertEquals(0, ScmControlAxes.groundRecoverySpeedLimit(2, 0, 7, 2));
    }

    // Follow a committed reverse route with one inversion at the wheel and bearing boundary
    @Test void recoversWhileReversing(){
        Vec3 pos = new Vec3(8, 0, 100);
        Vec3 up = new Vec3(0, 1, 0);
        double yaw = 0.0D;
        double rate = 0.0D;
        ScmGroundSteering steering = new ScmGroundSteering();
        for(int idx = 0; idx < 1600; idx++){
            Vec3 forward = new Vec3(Math.sin(yaw), 0, Math.cos(yaw));
            Vec3 target = SablePathfinder.routeLegTrackingTarget(new Vec3(0, 0, 200),
                    new Vec3(0, 0, -800), pos, SablePathfinder.RouteMode.GROUND, 8, 0);
            Vec3 facing = target.subtract(pos).normalize().scale(-1.0D);
            double error = Math.atan2(forward.cross(facing).y, forward.dot(facing));
            double input = steering.update(error, 0, -3, rate, 0, 0.05D);
            double actual = ScmControlAxes.wheelSteeringDemand(up.scale(input), up, true);
            actual = ScmControlAxes.steeringSignal(actual, 15) / 15.0D;
            rate = -3 * Math.tan(actual * Math.toRadians(30)) / 4;
            yaw += rate * 0.05D;
            pos = pos.add(forward.scale(-0.15D));
        }
        assertTrue(Math.abs(pos.x) < 0.35D, "lateral error=" + pos.x);
        assertTrue(Math.cos(yaw) > Math.cos(Math.toRadians(5)));
        assertTrue(pos.z < 0);
        var poses = List.of(new ScmPoseControl.SteeringPose(-30, 0, -100),
                new ScmPoseControl.SteeringPose(0, 0, 0), new ScmPoseControl.SteeringPose(30, 0, 100));
        double reverse = ScmControlAxes.wheelSteeringDemand(up.scale(0.5D), up, true);
        assertEquals(-15, ScmPoseControl.steeringTarget(poses, reverse).angleX(), 1.0E-9D);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Follow the same front-axle preview and discrete signal used by wheel mounts
    private static void simulateRecovery(double offset, double degrees){
        WaypointSpline spline = straightRoute();
        ScmGroundSteering steering = new ScmGroundSteering();
        var vehicle = capabilities();
        Vec3 pos = new Vec3(offset, 0, 30);
        double yaw = Math.toRadians(degrees);
        double rate = 0.0D;
        double errorSum = 0.0D;
        for(int idx = 0; idx < 1600; idx++){
            Vec3 forward = new Vec3(Math.sin(yaw), 0, Math.cos(yaw));
            var route = control(spline, pos.add(forward.scale(2.0D)), forward, vehicle);
            Vec3 target = route.steeringDirection();
            double error = Math.atan2(forward.cross(target).y, forward.dot(target));
            double speed = ScmControlAxes.groundRecoverySpeedLimit(error, 3,
                    vehicle.minimumTurningRadius(), 2.0D);
            double input = steering.update(error, route.signedCurvature(), speed, rate,
                    route.steeringFeedForward(), 0.05D);
            double actual = ScmControlAxes.steeringSignal(input, 15) / 15.0D;
            rate = speed * Math.tan(actual * vehicle.maximumSteeringRadians()) / vehicle.wheelbase();
            yaw += rate * 0.05D;
            pos = pos.add(forward.scale(speed * 0.05D));
            if(idx >= 1500) errorSum += Math.abs(pos.x);
        }
        assertTrue(errorSum / 100.0D < 0.35D, "offset=" + offset + ", heading=" + degrees + ", error=" + errorSum / 100);
        assertTrue(Math.cos(yaw) > Math.cos(Math.toRadians(5.0D)), "heading=" + degrees + ", final=" + yaw);
        assertTrue(pos.z > 100.0D);
    }

    // Supply a long retained route which does not terminate during recovery
    private static WaypointSpline straightRoute(){
        return WaypointSpline.of(List.of(Vec3.ZERO, new Vec3(0, 0, 400), new Vec3(0, 0, 800)));
    }

    // Match Offroad's thirty degree steering travel
    private static GroundPathPlanner.VehicleCapabilities capabilities(){
        return new GroundPathPlanner.VehicleCapabilities(4, Math.toRadians(30), 0, 0, 0, true);
    }

    // Resolve the live ground spline controller
    private static GroundPathPlanner.ForwardRouteControl control(WaypointSpline spline, Vec3 pos, Vec3 forward,
                                                                 GroundPathPlanner.VehicleCapabilities vehicle){
        return GroundPathPlanner.forwardSplineControl(spline, spline.project(pos, 0), pos, forward,
                vehicle, 3, 3, 2, 2, 0.75D, 0.5D);
    }
}
