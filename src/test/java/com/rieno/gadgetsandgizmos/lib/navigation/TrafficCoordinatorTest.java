package com.rieno.gadgetsandgizmos.lib.navigation;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import com.google.gson.Gson;

import static org.junit.jupiter.api.Assertions.*;

// Recover nearby stopped traffic without changing unrelated routes or accepting unverified moves
class TrafficCoordinatorTest{
    private static final UUID FIRST = new UUID(0, 1), SECOND = new UUID(0, 2);
    private static final List<AABB> HULL = List.of(new AABB(-1, -1, -1, 1, 1, 1));

    // Replay the stopped fleet captured from the server, including long offset connector hulls
    @Test
    void liveBlockedFleetReceivesAStableRecoveryMove() throws Exception{
        try(var input = getClass().getResourceAsStream("/navigation/blocked-fleet.json")){
            assertNotNull(input);
            var participants = new Gson().fromJson(new InputStreamReader(input, StandardCharsets.UTF_8), RouteTrafficPriority.Participant[].class);
            var fleet = java.util.Arrays.stream(participants).map(participant -> new TrafficCoordinator.Vehicle(participant,
                    true, participant.velocity().length() < 0.25D, true)).toList();
            var coordinator = new TrafficCoordinator();
            coordinator.update(0, fleet, null, clear());
            for(long tick = 20; tick < 28; tick++) coordinator.update(tick, fleet, null, clear());
            var mover = fleet.stream().filter(vehicle -> coordinator.directive(vehicle.participant().vehicleId()).motion() != null)
                    .findFirst().orElseThrow(() -> new AssertionError("The captured fleet remained stopped without a clearing move"));
            var directive = coordinator.directive(mover.participant().vehicleId());
            var motion = directive.motion();
            assertTrue(motion.target().distanceTo(motion.start()) > 2.0D);
            coordinator.update(28, fleet, null, clear());
            assertEquals(motion, coordinator.directive(mover.participant().vehicleId()).motion());
        }
    }

    @Test
    void moveAsideIsRetainedUntilTheWaitingShipHasPassed(){
        var coordinator = new TrafficCoordinator();
        var first = vehicle(FIRST, Vec3.ZERO, new Vec3(20, 0, 0), true, true);
        var second = vehicle(SECOND, new Vec3(4, 0, 0), new Vec3(-20, 0, 0), true, true);
        coordinator.update(0, List.of(first, second), null, clear());
        coordinator.update(20, List.of(first, second), null, clear());
        var motion = coordinator.directive(SECOND).motion();
        assertNotNull(motion);
        assertEquals(FIRST, motion.clearingFor());
        var moved = vehicle(SECOND, motion.target(), new Vec3(-20, 0, 0), true, false);
        coordinator.update(40, List.of(first, moved), null, clear());
        assertEquals(motion, coordinator.directive(SECOND).motion());
        var passed = vehicle(FIRST, new Vec3(12, 0, 0), new Vec3(20, 0, 0), true, false);
        coordinator.update(41, List.of(passed, moved), null, clear());
        assertEquals(TrafficCoordinator.Action.FOLLOW, coordinator.directive(SECOND).action());
        assertEquals(new Vec3(-20, 0, 0), moved.participant().route().getLast());
    }

    @Test
    void pendingEscapeUsesOneStableProbePerFrame(){
        var coordinator = new TrafficCoordinator();
        var first = vehicle(FIRST, Vec3.ZERO, new Vec3(20, 0, 0), true, true);
        var second = vehicle(SECOND, new Vec3(4, 0, 0), new Vec3(-20, 0, 0), true, true);
        var probes = new AtomicInteger();
        var targets = new java.util.ArrayList<Vec3>();
        TrafficCoordinator.EscapeProbe probe = (id, start, target) -> {
            probes.incrementAndGet(); targets.add(target); return TrafficCoordinator.Clearance.PENDING;
        };
        coordinator.update(0, List.of(first, second), null, probe);
        coordinator.update(20, List.of(first, second), null, probe);
        coordinator.update(20, List.of(first, second), null, probe);
        coordinator.update(21, List.of(first, second), null, probe);
        assertEquals(2, probes.get());
        assertEquals(targets.getFirst(), targets.getLast());
        assertEquals(TrafficCoordinator.Action.WAIT, coordinator.directive(SECOND).action());
    }

    @Test
    void unverifiedDirectionsAndOtherFleetHullsCannotBeUsed(){
        var coordinator = new TrafficCoordinator();
        var first = vehicle(FIRST, Vec3.ZERO, new Vec3(20, 0, 0), true, true);
        var second = vehicle(SECOND, new Vec3(4, 0, 0), new Vec3(-20, 0, 0), true, true);
        var parked = vehicle(new UUID(0, 3), new Vec3(4, 0, -4), new Vec3(4, 0, -4), false, false);
        var targets = new java.util.ArrayList<Vec3>();
        TrafficCoordinator.EscapeProbe probe = (id, start, target) -> {
            targets.add(target); return targets.size() == 1 ? TrafficCoordinator.Clearance.BLOCKED : TrafficCoordinator.Clearance.CLEAR;
        };
        coordinator.update(0, List.of(first, second, parked), null, probe);
        coordinator.update(20, List.of(first, second, parked), null, probe);
        assertNull(coordinator.directive(SECOND).motion());
        coordinator.update(21, List.of(first, second, parked), null, probe);
        assertNotNull(coordinator.directive(SECOND).motion());
        assertFalse(targets.contains(parked.participant().position()));
        assertNotEquals(targets.getFirst(), coordinator.directive(SECOND).motion().target());
    }

    @Test
    void separateRoutesAndDockedShipsDoNotReceiveRecoveryCommands(){
        var coordinator = new TrafficCoordinator();
        var first = vehicle(FIRST, Vec3.ZERO, new Vec3(20, 0, 0), true, true);
        var separate = vehicle(SECOND, new Vec3(4, 12, 0), new Vec3(-20, 12, 0), true, true);
        var parked = vehicle(new UUID(0, 3), new Vec3(4, 0, 0), new Vec3(4, 0, 0), false, false);
        coordinator.update(0, List.of(first, separate, parked), null, clear());
        coordinator.update(20, List.of(first, separate, parked), null, clear());
        assertEquals(TrafficCoordinator.Action.FOLLOW, coordinator.directive(SECOND).action());
        assertEquals(TrafficCoordinator.Action.FOLLOW, coordinator.directive(new UUID(0, 3)).action());
    }

    @Test
    void transientCrossingTrafficDoesNotTriggerARecovery(){
        var coordinator = new TrafficCoordinator();
        var first = vehicle(FIRST, Vec3.ZERO, new Vec3(20, 0, 0), true, true);
        var second = vehicle(SECOND, new Vec3(4, 0, 0), new Vec3(-20, 0, 0), true, true);
        coordinator.update(0, List.of(first, second), null, (id, start, target) -> { fail("Recovery began before a sustained stall"); return null; });
        coordinator.update(19, List.of(first, second), null, (id, start, target) -> { fail("Recovery began before a sustained stall"); return null; });
        assertNull(coordinator.directive(SECOND).motion());
    }

    @Test
    void aBlockedFreeFlyingShipCanDetourAroundAnImmovableHull(){
        var coordinator = new TrafficCoordinator();
        var first = new TrafficCoordinator.Vehicle(vehicle(FIRST, Vec3.ZERO, new Vec3(20, 0, 0), true, true).participant(), true, true, true);
        var parked = vehicle(SECOND, new Vec3(4, 0, 0), new Vec3(4, 0, 0), false, false);
        coordinator.update(0, List.of(first, parked), null, clear());
        coordinator.update(20, List.of(first, parked), null, clear());
        assertNotNull(coordinator.directive(FIRST).motion());
        assertNull(coordinator.directive(SECOND).motion());
    }

    @Test
    void departedParticipantsCannotKeepARecoveryReservation(){
        var coordinator = new TrafficCoordinator();
        var first = vehicle(FIRST, Vec3.ZERO, new Vec3(20, 0, 0), true, true);
        var second = vehicle(SECOND, new Vec3(4, 0, 0), new Vec3(-20, 0, 0), true, true);
        coordinator.update(0, List.of(first, second), null, clear());
        coordinator.update(20, List.of(first, second), null, clear());
        assertNotNull(coordinator.directive(SECOND).motion());
        coordinator.update(21, List.of(second), null, clear());
        assertNull(coordinator.directive(SECOND).motion());
    }

    @Test
    void aNewHullReopensAnAcceptedEscape(){
        var coordinator = new TrafficCoordinator();
        var first = vehicle(FIRST, Vec3.ZERO, new Vec3(20, 0, 0), true, true);
        var second = vehicle(SECOND, new Vec3(4, 0, 0), new Vec3(-20, 0, 0), true, true);
        coordinator.update(0, List.of(first, second), null, clear());
        coordinator.update(20, List.of(first, second), null, clear());
        Vec3 prev = coordinator.directive(SECOND).motion().target();
        var obstacle = vehicle(new UUID(0, 3), prev, prev, false, false);
        coordinator.update(21, List.of(first, second, obstacle), null, clear());
        assertTrue(coordinator.directive(SECOND).motion() == null || !prev.equals(coordinator.directive(SECOND).motion().target()));
    }

    // Advance a stopped group with physical hull checks until every ship completes its original trip
    @Test
    void stoppedGroupClearsEachOtherAndResumesEveryDestination(){
        var coordinator = new TrafficCoordinator();
        var positions = new java.util.LinkedHashMap<UUID, Vec3>();
        positions.put(FIRST, Vec3.ZERO);
        positions.put(SECOND, new Vec3(4, 0, 0));
        UUID third = new UUID(0, 3);
        positions.put(third, new Vec3(8, 0, 0));
        var goals = java.util.Map.of(FIRST, new Vec3(20, 0, 0), SECOND, new Vec3(-20, 0, 0), third, new Vec3(-24, 0, 0));
        var blocked = new java.util.HashSet<>(positions.keySet());
        var collisions = new java.util.HashSet<UUID>();
        for(long tick = 0; tick < 800; tick++){
            var fleet = positions.entrySet().stream().map(entry -> new TrafficCoordinator.Vehicle(
                    vehicle(entry.getKey(), entry.getValue(), goals.get(entry.getKey()), true, blocked.contains(entry.getKey())).participant(),
                    true, blocked.contains(entry.getKey()), collisions.contains(entry.getKey()))).toList();
            coordinator.update(tick, fleet, null, clear());
            for(UUID id : positions.keySet()){
                var directive = coordinator.directive(id);
                if(directive.action() == TrafficCoordinator.Action.WAIT || directive.motion() == null && directive.traffic().yield()){
                    blocked.add(id); continue;
                }
                Vec3 start = positions.get(id), target = directive.motion() == null ? goals.get(id) : directive.motion().target();
                Vec3 delta = target.subtract(start);
                if(delta.length() <= 0.05D){ blocked.remove(id); continue; }
                Vec3 end = start.add(delta.normalize().scale(Math.min(0.2D, delta.length())));
                boolean collision = positions.entrySet().stream().filter(entry -> !entry.getKey().equals(id)).anyMatch(entry ->
                        DockTrafficGeometry.routesConflict(List.of(start, end), HULL, List.of(entry.getValue()), HULL));
                if(collision){ blocked.add(id); collisions.add(id); }
                else{ positions.put(id, end); blocked.remove(id); collisions.remove(id); }
            }
        }
        assertTrue(positions.get(FIRST).distanceTo(goals.get(FIRST)) < 0.25D, "The first ship did not resume its trip");
        assertTrue(positions.get(SECOND).distanceTo(goals.get(SECOND)) < 0.25D, "The yielding ship did not resume its trip");
        assertTrue(positions.get(third).distanceTo(goals.get(third)) < 0.25D, "The third ship did not resume its trip: " + positions);
    }

    private static TrafficCoordinator.EscapeProbe clear(){ return (id, start, target) -> TrafficCoordinator.Clearance.CLEAR; }
    private static TrafficCoordinator.Vehicle vehicle(UUID id, Vec3 pos, Vec3 target, boolean movable, boolean blocked){
        var participant = new RouteTrafficPriority.Participant(id, pos, Vec3.ZERO, List.of(pos, target),
                SablePathfinder.RouteMode.FLIGHT, 1, 1, 4, HULL);
        return new TrafficCoordinator.Vehicle(participant, movable, blocked, false);
    }
}
