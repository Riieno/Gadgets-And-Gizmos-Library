package com.rieno.gadgetsandgizmos.lib.shipping;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Verify graph and schedule clients share exclusive physical berths and safe holding geometry
class ShipDockProvisioningTest{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @BeforeAll static void prepareRegistries(){
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        net.neoforged.fml.ModList.of(List.of(), List.of());
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test void graphAndScheduleChannelsCannotProvisionTheSameConnector(){
        MinecraftServer server = mock(MinecraftServer.class);
        when(server.getTickCount()).thenReturn(20, 21, 22, 23);
        var scheduler = new ShipDockScheduler();
        var pilot = owner("dock");
        var graph = owner("scm_graph_dock");
        var slot = slot("world|connector_a");
        var alias = new ShipDockScheduler.DockSlot(UUID.randomUUID(), slot.connectorKey());
        var priority = new ShipDockScheduler.RequestPriority(1, 0, true);
        var vessel = new ShipDockScheduler.VesselEnvelope(3, 4, 2);
        assertTrue(scheduler.request(server, pilot, List.of(slot), Set.of(), null, priority, vessel).granted());
        assertFalse(scheduler.request(server, graph, List.of(alias), Set.of(), null, priority, vessel).granted());
        scheduler.release(pilot);
        assertTrue(scheduler.request(server, graph, List.of(alias), Set.of(), null, priority, vessel).granted());
    }

    @Test void occupiedOrProvisionedBerthsRedirectArrivalsToAnotherConnector(){
        MinecraftServer server = mock(MinecraftServer.class);
        when(server.getTickCount()).thenReturn(100);
        var scheduler = new ShipDockScheduler();
        var first = slot("world|connector_a");
        var second = new ShipDockScheduler.DockSlot(first.dockId(), "world|connector_b");
        var priority = new ShipDockScheduler.RequestPriority(1, 0, true);
        var vessel = new ShipDockScheduler.VesselEnvelope(3, 4, 2);
        assertTrue(scheduler.request(server, owner("dock"), List.of(first), Set.of(), null, priority, vessel).granted());
        var diverted = scheduler.request(server, owner("scm_graph_dock"), List.of(first, second),
                Set.of(first), null, priority, vessel);
        assertTrue(diverted.granted());
        assertEquals(second.connectorKey(), diverted.connectorKey());
    }

    @Test void committedApproachesCannotDisplaceAnotherShipsLeaseWithoutPhysicalOwnership(){
        var scheduler = new ShipDockScheduler();
        var berth = slot("world|connector_a");
        var vessel = new ShipDockScheduler.VesselEnvelope(4, 6, 3);
        var incumbent = owner("dock");
        var newcomer = owner("scm_graph_dock");
        var committed = new ShipDockScheduler.RequestPriority(5, 0, true);
        assertTrue(scheduler.request(incumbent, List.of(berth), Set.of(), null, committed, vessel, 0).granted());
        assertFalse(scheduler.request(newcomer, List.of(berth), Set.of(), null,
                new ShipDockScheduler.RequestPriority(0, 0, true), vessel, 1).granted());
        assertTrue(scheduler.request(incumbent, List.of(berth), Set.of(), null, committed, vessel, 2).granted());
    }

    @Test void queuedShipsReceiveSeparatedPositionsOutsideTheApproachCorridor(){
        var scheduler = new ShipDockScheduler();
        var berth = slot("world|connector_a");
        var vessel = new ShipDockScheduler.VesselEnvelope(5, 8, 4);
        var priority = new ShipDockScheduler.RequestPriority(2, 0, true);
        scheduler.request(owner("dock"), List.of(berth), Set.of(), null, priority, vessel, 0);
        var first = scheduler.request(owner("queue_a"), List.of(berth), Set.of(), null, priority, vessel, 0);
        var second = scheduler.request(owner("queue_b"), List.of(berth), Set.of(), null, priority, vessel, 0);
        List<List<Vec3>> corridors = List.of(List.of(Vec3.ZERO, new Vec3(0, 0, 30)));
        Vec3 a = ShipDockScheduler.resolveHoldingPosition(Vec3.ZERO, new Vec3(0, 0, 1),
                first.holdingPlacement(), first.holdingPlacement().vertical(), corridors, 12);
        Vec3 b = ShipDockScheduler.resolveHoldingPosition(Vec3.ZERO, new Vec3(0, 0, 1),
                second.holdingPlacement(), second.holdingPlacement().vertical(), corridors, 12);
        assertFalse(first.granted());
        assertFalse(second.granted());
        assertTrue(a.distanceTo(b) >= vessel.horizontalRadius() * 2);
        assertTrue(Math.abs(a.x) >= 12 && Math.abs(b.x) >= 12);
    }

    // Give each physical ship an independent reservation channel
    private static ShipDockScheduler.RequestKey owner(String channel){
        return new ShipDockScheduler.RequestKey(UUID.randomUUID(), channel);
    }

    // Identify the physical connector independently from its dock label
    private static ShipDockScheduler.DockSlot slot(String key){
        return new ShipDockScheduler.DockSlot(UUID.randomUUID(), key);
    }
}
