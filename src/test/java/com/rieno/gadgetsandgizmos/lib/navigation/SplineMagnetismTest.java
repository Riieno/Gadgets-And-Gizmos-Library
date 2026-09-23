package com.rieno.gadgetsandgizmos.lib.navigation;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SplineMagnetismTest {
    @Test
    void pullsBackToTheCentrelineWithoutReversingTheRouteTangent() {
        WaypointSpline spline = WaypointSpline.of(List.of(
                new Vec3(0.0D, 0.0D, 0.0D), new Vec3(20.0D, 0.0D, 0.0D)));
        WaypointSpline.Projection projection = spline.project(new Vec3(5.0D, 0.0D, 4.0D));

        SplineMagnetism.Guidance guidance = SplineMagnetism.guide(
                new Vec3(5.0D, 0.0D, 4.0D), projection, 4.0D, 3.0D,
                SplineMagnetism.AxisPolicy.ALL);

        assertTrue(guidance.active());
        assertTrue(guidance.travelDirection().x > 0.0D);
        assertTrue(guidance.travelDirection().z < 0.0D);
        assertEquals(1.0D, guidance.facingDirection().x, 1.0E-6D);
    }

    @Test
    void groundPolicyDoesNotPullVertically() {
        WaypointSpline spline = WaypointSpline.of(List.of(
                new Vec3(0.0D, 8.0D, 0.0D), new Vec3(20.0D, 8.0D, 0.0D)));
        Vec3 position = new Vec3(5.0D, 2.0D, 3.0D);

        SplineMagnetism.Guidance guidance = SplineMagnetism.guide(
                position, spline.project(position), 4.0D, 3.0D,
                SplineMagnetism.AxisPolicy.HORIZONTAL);

        assertEquals(0.0D, guidance.travelDirection().y, 1.0E-8D);
        assertEquals(0.0D, guidance.crossTrackCorrection().y, 1.0E-8D);
    }

    @Test
    void dampsSidewaysMotionBeforeItCrossesTheCentreline() {
        WaypointSpline spline = WaypointSpline.of(List.of(
                new Vec3(0.0D, 0.0D, 0.0D), new Vec3(20.0D, 0.0D, 0.0D)));
        Vec3 position = new Vec3(5.0D, 0.0D, 0.25D);

        SplineMagnetism.Guidance guidance = SplineMagnetism.guide(
                position, new Vec3(2.0D, 0.0D, -2.0D), spline.project(position),
                4.0D, 3.0D, 2.0D, SplineMagnetism.AxisPolicy.ALL);

        assertTrue(guidance.active());
        assertTrue(guidance.travelDirection().x > 0.0D);
        assertTrue(guidance.travelDirection().z > 0.0D);
    }

    @Test
    void cancelsSidewaysDriftWhileAlreadyOnTheCentreline() {
        WaypointSpline spline = WaypointSpline.of(List.of(
                new Vec3(0.0D, 0.0D, 0.0D), new Vec3(20.0D, 0.0D, 0.0D)));
        Vec3 position = new Vec3(5.0D, 0.0D, 0.0D);

        SplineMagnetism.Guidance guidance = SplineMagnetism.guide(
                position, new Vec3(2.0D, 0.0D, 1.0D), spline.project(position),
                4.0D, 3.0D, 2.0D, SplineMagnetism.AxisPolicy.ALL);

        assertTrue(guidance.travelDirection().z < 0.0D);
    }
}
