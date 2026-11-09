package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import com.rieno.gadgetsandgizmos.lib.navigation.DockingApproachAxis;
import com.rieno.gadgetsandgizmos.lib.navigation.SplineRouteTraversal;
import com.rieno.gadgetsandgizmos.lib.navigation.WaypointSpline;
import java.util.List;
import org.junit.jupiter.api.Test;
import net.minecraft.resources.ResourceLocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

// Preserve native pilot signs when a craft uses a different local forward axis
class ScmPilotControlTest{
    // Turn without walking an arc when a route points behind the native pilot
    @Test void turnsInPlaceBeforeDrivingAndStopsInsideArrivalTolerance(){
        var mode = new ScmPilotNavigationMode(ResourceLocation.fromNamespaceAndPath("test", "native"), "Native",
                host -> true, host -> ScmPilotControl.Profile.GROUND).bind(null);
        Vec3 forward = new Vec3(0, 0, -1), up = new Vec3(0, 1, 0);
        var turn = mode.navigate(sample(forward, up, forward.scale(-10), forward.scale(-1), Vec3.ZERO, 0.1D));
        assertEquals(Vec3.ZERO, turn.force());
        assertEquals(0, turn.driveStrength());
        assertTrue(Math.abs(turn.torque().y) > 0.5D);
        var approach = mode.navigate(sample(forward, up, forward.scale(0.15D), forward, Vec3.ZERO, 0.1D));
        assertEquals(0.1D, approach.driveStrength() * 5, 1.0E-9D);
        var arrived = mode.navigate(sample(forward, up, forward.scale(0.05D), forward.scale(-1), Vec3.ZERO, 0.1D));
        assertEquals(Vec3.ZERO, arrived.force());
        assertEquals(Vec3.ZERO, arrived.torque());
        assertEquals(0, arrived.driveStrength());
    }

    // Preserve connector orientation while backing or strafing into the final docking point
    @Test void alignsBeforeIndependentConnectorTranslation(){
        var mode = new ScmPilotNavigationMode(ResourceLocation.fromNamespaceAndPath("test", "native"), "Native",
                host -> true, host -> new ScmPilotControl.Profile(true, false, false)).bind(null);
        Vec3 forward = new Vec3(0, 0, -1), up = new Vec3(0, 1, 0), right = new Vec3(1, 0, 0);
        var input = sample(forward, up, right.scale(2), right, Vec3.ZERO, 0.1D);
        assertTrue(mode.navigate(input, forward).force().dot(right) > 0);
        assertEquals(0, mode.navigate(input, forward).torque().y, 1.0E-9D);
        assertEquals(Vec3.ZERO, mode.navigate(input, right).force());
        var heightOnly = sample(forward, up, up, up, Vec3.ZERO, 0.1D);
        assertEquals(Vec3.ZERO, mode.navigate(heightOnly, right).force());
        assertTrue(Math.abs(mode.navigate(heightOnly, right).torque().y) > 0.5D);
    }

    // Retain yaw information from connector roll when a contact faces vertically
    @Test void rotatesVehicleHeadingFromVerticalConnectorAlignment(){
        Vec3 forward = new Vec3(0, 0, -1), down = new Vec3(0, -1, 0), right = new Vec3(1, 0, 0);
        Vec3 heading = ScmDockingOrientation.alignedDirection(forward, down, forward, down, right);
        assertEquals(1, heading.dot(right), 1.0E-9D);
        assertEquals(forward, ScmDockingOrientation.alignedDirection(forward, down, forward, down, forward));
    }

    // Accept only connector overlap with the finite detection axis, including both ends
    @Test void acceptsHandoffAlongTheEntireDetectionRange(){
        var axis = new DockingApproachAxis(Vec3.ZERO, new Vec3(0, 1, 0), 20);
        for(double y : new double[]{0, 1, 7, 19, 20}){
            assertTrue(axis.overlaps(AABB.ofSize(new Vec3(0.5D, y, 0), 1, 1, 1)));
            assertEquals(y, axis.closestPoint(new Vec3(4, y, 0)).y, 1.0E-9D);
        }
        assertFalse(axis.overlaps(AABB.ofSize(new Vec3(1.1D, 10, 0), 1, 1, 1)));
        assertFalse(axis.overlaps(AABB.ofSize(new Vec3(0, 21, 0), 1, 1, 1)));
        assertFalse(axis.overlaps(AABB.ofSize(new Vec3(0, -1, 0), 1, 1, 1)));
        assertEquals(Vec3.ZERO, axis.closestPoint(new Vec3(0, -10, 0)));
        assertEquals(axis.endpoint(), axis.closestPoint(new Vec3(0, 30, 0)));
    }

    // Accept a lane entry touched by any hull member without extending connector handoff bounds
    @Test void capturesLaneEntryWithHullOverlap(){
        var bounds = List.of(new AABB(-5, -1, -1, -3, 1, 1), new AABB(2, -1, -1, 4, 1, 1));
        assertTrue(SplineRouteTraversal.entryOverlaps(new Vec3(-4, 0, 0), bounds));
        assertTrue(SplineRouteTraversal.entryOverlaps(new Vec3(4, 0, 0), bounds));
        assertFalse(SplineRouteTraversal.entryOverlaps(Vec3.ZERO, bounds));
        assertFalse(SplineRouteTraversal.entryOverlaps(new Vec3(6, 0, 0), bounds));
        var route = new SplineRouteTraversal(WaypointSpline.of(List.of(new Vec3(-4, 0, 0), new Vec3(10, 0, 0))), true);
        assertTrue(route.acquireEntry(bounds));
        assertTrue(route.acquireEntry(List.of()));
        assertFalse(route.captured());
        assertTrue(SplineRouteTraversal.fromTag(route.toTag()).orElseThrow().entryAcquired());
        assertTrue(route.withContinuation(WaypointSpline.of(List.of(new Vec3(4, 0, 0), new Vec3(10, 0, 0))), 0.1D).isEmpty());
    }

    // Supply route samples with an independent speed limit
    private static ScmControlMode.ControlInput sample(Vec3 forward, Vec3 up, Vec3 target, Vec3 path, Vec3 angular, double tolerance){
        return new ScmControlMode.ControlInput(Vec3.ZERO, Vec3.ZERO, angular, forward, up, forward.cross(up),
                target, path, Vec3.ZERO, 5, tolerance, 1, 64, 64, false, true);
    }

    // Keep positive right turns consistent with physical yaw torque
    @Test void resolvesMovementAndYawInEveryHorizontalFrame(){
        for(Vec3 forward : new Vec3[]{new Vec3(0, 0, -1), new Vec3(1, 0, 0), new Vec3(0, 0, 1), new Vec3(-1, 0, 0)}){
            Vec3 up = new Vec3(0, 1, 0);
            Vec3 right = forward.cross(up);
            var input = ScmPilotControl.input(forward.scale(.5).add(right.scale(-.25)), up.scale(-.75), forward, up);
            assertEquals(.5, input.forward(), 1.0E-9);
            assertEquals(-.25, input.strafe(), 1.0E-9);
            assertEquals(.75, input.yaw(), 1.0E-9);
            assertEquals(0, input.lift(), 1.0E-9);
        }
    }

    // Keep invalid optional-controller input neutral and finite
    @Test void clampsNativeAxesAndRejectsNonFiniteDemand(){
        var input = new ScmPilotControl.Input(3, -2, Double.NaN, Double.POSITIVE_INFINITY, .5, -.5);
        assertEquals(new ScmPilotControl.Input(1, -1, 0, 0, .5, -.5), input);
    }

    // Maintain a selected cruise gear and regulate speeds below the lowest native gear
    @Test void holdsCruiseSpeedAndReducesGearForDocking(){
        double[] speeds = {0.5D, 1, 2.2D, 4, 5.5D};
        assertEquals(3, ScmPilotSpeedControl.gear(3, speeds));
        assertEquals(0, ScmPilotSpeedControl.gear(0.1D, speeds));
        var control = new ScmPilotSpeedControl();
        int held = 0;
        for(int tick = 0; tick < 100; tick++){
            boolean drive = control.drive(tick, 0.1D, 0.1D, 0.5D);
            assertEquals(drive, control.drive(tick, 0.1D, 0.1D, 0.5D));
            if(drive) held++;
        }
        assertTrue(held >= 19 && held <= 22, "Slow docking should regulate effort below first gear");
        assertFalse(control.drive(101, 0.1D, 2, 0.5D));
        assertFalse(control.drive(102, 0, 0, 0.5D));
    }

    // Raise or lower the native body without oscillating inside connector tolerance
    @Test void respectsHeightToleranceAndVerticalLimits(){
        assertEquals(1, ScmPilotHeightControl.direction(0.5D, 0.1D));
        assertEquals(-1, ScmPilotHeightControl.direction(-0.5D, 0.1D));
        assertEquals(0, ScmPilotHeightControl.direction(0.05D, 0.1D));
        assertEquals(4, ScmPilotHeightControl.velocity(100, 0, 0.1D, 4, 6));
        assertEquals(-6, ScmPilotHeightControl.velocity(-100, 0, 0.1D, 4, 6));
        assertEquals(-1, ScmPilotHeightControl.velocity(0, 2, 0.1D, 4, 6));
    }

    // Preserve native traversal along a wall or inverted ceiling without world avoidance
    @Test void selectsSurfaceNavigationFromLiveCapability(){
        var mode = new ScmPilotNavigationMode(ResourceLocation.fromNamespaceAndPath("test", "native"), "Native",
                host -> true, host -> new ScmPilotControl.Profile(true, false, false)).bind(null);
        assertFalse(mode.usesGroundNavigation());
        assertFalse(mode.avoidsWorldCollisions());
        for(Vec3 up : new Vec3[]{new Vec3(1, 0, 0), new Vec3(0, -1, 0)}){
            Vec3 forward = up.x != 0 ? new Vec3(0, 1, 0) : new Vec3(0, 0, -1);
            var input = new ScmControlMode.ControlInput(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO,
                    forward, up, forward.cross(up), forward.scale(20), forward, Vec3.ZERO,
                    2, 0.1D, 1, 64, 64, false, true);
            assertTrue(mode.navigate(input).force().dot(forward) > 0);
            assertEquals(0, mode.navigate(input).force().dot(up), 1.0E-9D);
        }
    }
}
