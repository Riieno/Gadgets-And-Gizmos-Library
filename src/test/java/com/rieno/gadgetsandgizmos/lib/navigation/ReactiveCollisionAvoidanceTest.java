package com.rieno.gadgetsandgizmos.lib.navigation;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactiveCollisionAvoidanceTest {
    @Test
    void equalClearancePrefersTheLeastDisruptiveEscape() {
        Vec3 travel = new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 shallow = new Vec3(1.0D, 0.0D, 1.0D).normalize();
        Vec3 reverse = travel.scale(-1.0D);

        ReactiveCollisionAvoidance.Response response =
                ReactiveCollisionAvoidance.resolve(
                        new ReactiveCollisionAvoidance.Request(
                                travel, 0.5D, 4.0D, List.of(
                                new ReactiveCollisionAvoidance.EscapeCandidate(shallow, 6.0D),
                                new ReactiveCollisionAvoidance.EscapeCandidate(reverse, 6.0D))));

        assertTrue(response.hazard());
        assertTrue(response.escapeAvailable());
        assertTrue(shallow.dot(response.escapeDirection()) > 0.999999D);
    }

    @Test
    void substantiallyClearerOppositeEscapeRemainsAvailable() {
        Vec3 travel = new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 shallow = new Vec3(1.0D, 0.0D, 1.0D).normalize();
        Vec3 reverse = travel.scale(-1.0D);

        ReactiveCollisionAvoidance.Response response =
                ReactiveCollisionAvoidance.resolve(
                        new ReactiveCollisionAvoidance.Request(
                                travel, 0.5D, 4.0D, List.of(
                                new ReactiveCollisionAvoidance.EscapeCandidate(shallow, 4.5D),
                                new ReactiveCollisionAvoidance.EscapeCandidate(reverse, 9.0D))));

        assertTrue(reverse.dot(response.escapeDirection()) > 0.999999D);
    }
}
