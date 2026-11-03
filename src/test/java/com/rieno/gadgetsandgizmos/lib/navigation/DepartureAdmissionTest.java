package com.rieno.gadgetsandgizmos.lib.navigation;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// Keep departure ownership local while established traffic clears the merge
class DepartureAdmissionTest{
    private static final List<AABB> HULL = List.of(new AABB(-1, 0, -1, 1, 3, 1));

    @Test
    void reservationIncludesTheMergeWithoutClaimingTheWholeExit(){
        var plan = plan();
        var bounds = plan.reservationBounds(HULL);
        assertTrue(bounds.stream().anyMatch(box -> box.contains(new Vec3(6, 11, 0))));
        assertFalse(bounds.stream().anyMatch(box -> box.contains(new Vec3(80, 11, 0))));
        assertFalse(bounds.stream().anyMatch(box -> box.contains(new Vec3(6, 24, 0))));
        assertTrue(plan.reservationControls(HULL).getLast().distanceTo(plan.clearancePoint()) <= 8.001D);
    }

    @Test
    void trafficAlreadyCommittedToTheMergeMustPassBeforeRelease(){
        UUID owner = UUID.randomUUID();
        assertEquals(owner, plan().departureBlocker(HULL, List.of(), traffic(owner, -5, 4, 10), 2.5D));
    }

    @Test
    void distantFollowersCanBeStoppedByTheNewReservation(){
        assertNull(plan().departureBlocker(HULL, List.of(), traffic(UUID.randomUUID(), -30, 4, 10), 2.5D));
    }

    @Test
    void passedTrafficAndSeparateLanesDoNotPreventDeparture(){
        assertNull(plan().departureBlocker(HULL, List.of(), traffic(UUID.randomUUID(), 14, 4, 10), 2.5D));
        assertNull(plan().departureBlocker(HULL, List.of(), traffic(UUID.randomUUID(), -5, 4, 24), 2.5D));
    }

    @Test
    void aFreeExitCanBeReservedBehindTrafficFurtherAlongTheLane(){
        var path = DockingApproachPath.capture(Vec3.ZERO, new Vec3(0, 1, 0), new Vec3(0, 0, 1),
                new Vec3(0, 10, 0), List.of(new Vec3(-100, 10, 0), new Vec3(100, 10, 0)));
        var occupied = new DockTrafficGeometry.Obstacle(UUID.randomUUID(), List.of(new AABB(60, 10, -1, 64, 13, 1)));
        assertTrue(DockingDeparturePlan.selectReady(path, Vec3.ZERO, new Vec3(0, 1, 0), new Vec3(0, 0, 1),
                new Vec3(0, 10, 0), HULL, List.of(occupied), DockTrafficGeometry.Traffic.EMPTY, 2.5D).isPresent());
    }

    @Test
    void curvedMergeKeepsItsOrderedBends(){
        var plan = new DockingDeparturePlan(Vec3.ZERO, new Vec3(0, 1, 0), new Vec3(0, 0, 1), new Vec3(0, 10, 0),
                List.of(new Vec3(0, 10, 0), new Vec3(0, 10, 4), new Vec3(10, 10, 4), new Vec3(100, 10, 4)));
        var controls = plan.reservationControls(HULL);
        assertTrue(controls.contains(new Vec3(0, 10, 4)));
        assertEquals(new Vec3(4, 10, 4), controls.getLast());
    }

    private static DockingDeparturePlan plan(){
        return new DockingDeparturePlan(Vec3.ZERO, new Vec3(0, 1, 0), new Vec3(0, 0, 1), new Vec3(0, 10, 0),
                List.of(new Vec3(0, 10, 0), new Vec3(100, 10, 0)));
    }

    private static DockTrafficGeometry.Traffic traffic(UUID owner, double x, double speed, double y){
        return new DockTrafficGeometry.Traffic(List.of(), List.of(new DockTrafficGeometry.Movement(owner,
                List.of(new Vec3(x, y, 0), new Vec3(100, y, 0)), HULL, speed)));
    }
}
