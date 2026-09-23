package com.rieno.gadgetsandgizmos.lib.navigation;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointProgressTrackerTest {
    @Test
    void curveParameterCannotCompleteAWaypointWithoutPhysicalCapture() {
        WaypointProgressTracker tracker = new WaypointProgressTracker();
        Vec3 target = new Vec3(10.0D, 0.0D, 0.0D);
        tracker.reset(Vec3.ZERO, target, 0L, true);

        WaypointProgressTracker.Observation farFromEndpoint = tracker.observe(
                new Vec3(10.0D, 0.0D, 20.0D), target,
                1.0D, 1L, 20L, true, 0.999D);

        assertFalse(farFromEndpoint.captured());
        assertFalse(farFromEndpoint.capturedAtRouteProgress(0.999D, 0.65D));
    }

    @Test
    void sweptEndpointCaptureCompletesAfterSufficientCurveProgress() {
        WaypointProgressTracker tracker = new WaypointProgressTracker();
        Vec3 target = new Vec3(10.0D, 0.0D, 0.0D);
        tracker.reset(new Vec3(8.0D, 0.0D, 0.0D), target, 0L, true);

        WaypointProgressTracker.Observation crossedEndpoint = tracker.observe(
                new Vec3(12.0D, 0.0D, 0.0D), target,
                0.5D, 1L, 20L, true, 0.9D);

        assertTrue(crossedEndpoint.captured());
        assertTrue(crossedEndpoint.capturedAtRouteProgress(0.9D, 0.65D));
        assertFalse(crossedEndpoint.capturedAtRouteProgress(0.5D, 0.65D));
    }
}
