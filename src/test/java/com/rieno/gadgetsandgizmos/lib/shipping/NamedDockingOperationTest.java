package com.rieno.gadgetsandgizmos.lib.shipping;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class NamedDockingOperationTest{
    @Test void waitsForReservationNavigatesThenCompletesOnlyAfterPhysicalLock(){
        Host host = new Host();
        var op = new NamedDockingOperation<>(host, 600);
        assertTrue(op.start(" Cargo Dock "));
        assertEquals("Cargo Dock", op.name());
        op.tick(0);
        assertEquals(NamedDockingOperation.Phase.WAITING, op.phase());
        assertEquals(0, host.navigation);
        host.reservation = NamedDockingOperation.Reservation.GRANTED;
        op.tick(1);
        assertEquals(NamedDockingOperation.Phase.NAVIGATING, op.phase());
        assertTrue(op.pending());
        host.arrived = true;
        op.tick(2);
        assertEquals(NamedDockingOperation.Phase.DOCKING, op.phase());
        assertFalse(op.complete());
        host.locked = true;
        op.tick(3);
        assertTrue(op.complete());
        assertFalse(op.pending());
        assertEquals(1, host.stops);
        assertEquals(0, host.releases);
        op.tick(4);
        assertTrue(host.committed);
        op.cancel();
        assertEquals(1, host.releases);
        assertFalse(host.disconnected);
        assertEquals(NamedDockingOperation.Phase.IDLE, op.phase());
    }

    @Test void rejectsUnknownOrInvalidNamesWithoutCancellingAnExistingRequest(){
        Host host = new Host();
        var op = new NamedDockingOperation<>(host, 600);
        assertTrue(op.start("Cargo Dock"));
        assertFalse(op.start(""));
        assertFalse(op.start("Missing"));
        host.valid = false;
        assertFalse(op.start("Cargo Dock"));
        assertEquals("Cargo Dock", op.name());
        assertEquals(0, host.releases);
        op.tick(1);
        assertEquals(NamedDockingOperation.Phase.FAILED, op.phase());
        assertEquals(1, host.releases);
        assertFalse(op.complete());
    }

    @Test void reservationLossAndDockingTimeoutReleaseMovementAndConnectors(){
        Host host = new Host();
        host.reservation = NamedDockingOperation.Reservation.GRANTED;
        host.arrived = true;
        var op = new NamedDockingOperation<>(host, 5);
        op.start("Cargo Dock");
        op.tick(0);
        op.tick(5);
        assertEquals(NamedDockingOperation.Phase.FAILED, op.phase());
        assertEquals(1, host.releases);
        assertEquals(1, host.stops);
        op.tick(6);
        assertEquals(1, host.releases);
        op.cancel();
        assertEquals(1, host.releases);
        op.start("Cargo Dock");
        op.tick(7);
        host.reservation = NamedDockingOperation.Reservation.WAITING;
        op.tick(8);
        assertFalse(op.complete());
        assertTrue(op.pending());
        assertEquals(NamedDockingOperation.Phase.WAITING, op.phase());
        assertTrue(host.holds > 0);
    }

    @Test void missingMovementAndLostCompletedConnectionsNeverReportSuccess(){
        Host host = new Host();
        host.reservation = NamedDockingOperation.Reservation.GRANTED;
        host.moveAccepted = false;
        var op = new NamedDockingOperation<>(host, 600);
        op.start("Cargo Dock");
        op.tick(0);
        assertEquals(NamedDockingOperation.Phase.FAILED, op.phase());
        host.moveAccepted = true;
        host.arrived = true;
        host.locked = true;
        op.start("Cargo Dock");
        op.tick(1);
        assertTrue(op.complete());
        host.locked = false;
        op.tick(2);
        assertFalse(op.complete());
        assertEquals(NamedDockingOperation.Phase.FAILED, op.phase());
    }

    @Test void exactNamesAreCaseInsensitiveAndChooseTheNearestValidDock(){
        record Dock(String name, boolean valid, double distance){}
        var docks = List.of(new Dock("Cargo Dock", true, 100), new Dock("CARGO DOCK", false, 1),
                new Dock("Cargo Dock", true, 10), new Dock("Cargo Dock Annex", true, 0));
        assertEquals(docks.get(2), NamedDockingOperation.findNamed(docks, " cargo dock ",
                Dock::name, Dock::valid, Dock::distance).orElseThrow());
        assertTrue(NamedDockingOperation.findNamed(docks, "Cargo*", Dock::name, Dock::valid, Dock::distance).isEmpty());
    }

    @Test void anExistingPhysicalLockIsAdoptedWithoutMovingAway(){
        Host host = new Host();
        host.reservation = NamedDockingOperation.Reservation.GRANTED;
        host.locked = true;
        var op = new NamedDockingOperation<>(host, 600);
        assertTrue(op.start("Cargo Dock"));
        op.tick(0);
        assertTrue(op.complete());
        assertEquals(0, host.navigation);
        op.cancel();
        assertFalse(host.disconnected);
    }

    @Test void losingAnApproachReservationStopsMovementAndResumesWhenAvailable(){
        Host host = new Host();
        host.reservation = NamedDockingOperation.Reservation.GRANTED;
        var op = new NamedDockingOperation<>(host, 600);
        op.start("Cargo Dock");
        op.tick(0);
        assertEquals(1, host.navigation);
        host.reservation = NamedDockingOperation.Reservation.WAITING;
        op.tick(1);
        assertEquals(NamedDockingOperation.Phase.WAITING, op.phase());
        assertEquals(1, host.stops);
        host.reservation = NamedDockingOperation.Reservation.GRANTED;
        op.tick(2);
        assertEquals(NamedDockingOperation.Phase.NAVIGATING, op.phase());
        assertEquals(2, host.navigation);
        op.cancel();
        assertTrue(host.disconnected);
    }

    @Test void waitingRequestsKeepHoldingAndResumeDockingAfterAReservationReturns(){
        Host host = new Host();
        var op = new NamedDockingOperation<>(host, 600);
        op.start("Cargo Dock");
        op.tick(0);
        op.tick(1);
        assertEquals(2, host.holds);
        host.reservation = NamedDockingOperation.Reservation.GRANTED;
        host.arrived = true;
        op.tick(2);
        assertEquals(NamedDockingOperation.Phase.DOCKING, op.phase());
        host.reservation = NamedDockingOperation.Reservation.WAITING;
        op.tick(3);
        assertEquals(NamedDockingOperation.Phase.WAITING, op.phase());
        assertEquals(0, host.releases);
        assertEquals(3, host.holds);
        host.reservation = NamedDockingOperation.Reservation.GRANTED;
        host.arrived = false;
        op.tick(4);
        assertEquals(NamedDockingOperation.Phase.NAVIGATING, op.phase());
    }

    private static final class Host implements NamedDockingOperation.Host<String>{
        private NamedDockingOperation.Reservation reservation = NamedDockingOperation.Reservation.WAITING;
        private boolean valid = true, arrived, locked, committed, disconnected, moveAccepted = true;
        private int navigation, stops, releases, holds;
        public Optional<String> resolve(String name){ return name.equals("Cargo Dock") ? Optional.of(name) : Optional.empty(); }
        public boolean valid(String target){ return valid; }
        public NamedDockingOperation.Reservation reserve(String target, boolean committed){ this.committed = committed; return reservation; }
        public boolean waitForReservation(String target){ holds++; return true; }
        public boolean navigate(String target){ navigation++; return moveAccepted; }
        public boolean approachReached(String target){ return arrived; }
        public boolean dock(String target){ return true; }
        public boolean locked(String target){ return locked; }
        public void stopMovement(){ stops++; }
        public void release(String target, boolean disconnect){ disconnected = disconnect; releases++; }
    }
}
