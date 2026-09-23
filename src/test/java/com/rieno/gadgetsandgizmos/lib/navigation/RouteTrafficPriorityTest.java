package com.rieno.gadgetsandgizmos.lib.navigation;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteTrafficPriorityTest {
    private static final UUID FIRST = new UUID(0L, 1L);
    private static final UUID SECOND = new UUID(0L, 2L);

    @Test
    void headOnConflictDelegatesExactlyOneVehicleToYield() {
        RouteTrafficPriority.Participant first = participant(
                FIRST, new Vec3(0.0D, 0.0D, 0.0D),
                List.of(new Vec3(0.0D, 0.0D, 0.0D), new Vec3(20.0D, 0.0D, 0.0D)));
        RouteTrafficPriority.Participant second = participant(
                SECOND, new Vec3(20.0D, 0.0D, 0.0D),
                List.of(new Vec3(20.0D, 0.0D, 0.0D), new Vec3(0.0D, 0.0D, 0.0D)));

        RouteTrafficPriority.Decision firstDecision = RouteTrafficPriority.resolve(
                first, List.of(first, second), RouteTrafficPriority.Settings.DEFAULT);
        RouteTrafficPriority.Decision secondDecision = RouteTrafficPriority.resolve(
                second, List.of(first, second), RouteTrafficPriority.Settings.DEFAULT);

        assertTrue(firstDecision.conflict());
        assertTrue(secondDecision.conflict());
        assertFalse(firstDecision.yield());
        assertTrue(secondDecision.yield());
    }

    @Test
    void fasterFollowerYieldsWithoutForcingLeadVehicleOffRoute() {
        RouteTrafficPriority.Participant follower = new RouteTrafficPriority.Participant(
                SECOND, new Vec3(0.0D, 0.0D, 0.0D), new Vec3(2.0D, 0.0D, 0.0D),
                List.of(new Vec3(0.0D, 0.0D, 0.0D), new Vec3(24.0D, 0.0D, 0.0D)),
                SablePathfinder.RouteMode.GROUND, 1.5D, 1.0D, 2.0D);
        RouteTrafficPriority.Participant leader = new RouteTrafficPriority.Participant(
                FIRST, new Vec3(4.0D, 0.0D, 0.0D), new Vec3(0.5D, 0.0D, 0.0D),
                List.of(new Vec3(4.0D, 0.0D, 0.0D), new Vec3(24.0D, 0.0D, 0.0D)),
                SablePathfinder.RouteMode.GROUND, 1.5D, 1.0D, 0.5D);

        assertTrue(RouteTrafficPriority.resolve(
                follower, List.of(follower, leader), RouteTrafficPriority.Settings.DEFAULT).yield());
        assertFalse(RouteTrafficPriority.resolve(
                leader, List.of(follower, leader), RouteTrafficPriority.Settings.DEFAULT).yield());
    }

    private static RouteTrafficPriority.Participant participant(
            UUID id, Vec3 position, List<Vec3> route
    ) {
        return new RouteTrafficPriority.Participant(
                id, position, Vec3.ZERO, route, SablePathfinder.RouteMode.GROUND,
                1.5D, 1.0D, 1.0D);
    }
}
