package com.rieno.gadgetsandgizmos.lib.compat.simulated;

import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorPair;
import dev.simulated_team.simulated.content.blocks.redstone_magnet.MagnetMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SimulatedDockingRecoveryTest{
    @Test void unavailablePeerIsRetainedWithoutReadingOrLoadingItsChunk(){
        Level level = mock(Level.class);
        DockingConnectorBlockEntity ship = endpoint(level, new BlockPos(0, 10, 0));
        ship.otherConnectorPosition = new BlockPos(32, 10, 0);
        assertTrue(SimulatedDockingApi.isAwaitingPeer(ship));
        assertFalse(SimulatedDockingApi.restoreConnection(ship));
        assertEquals(new BlockPos(32, 10, 0), ship.otherConnectorPosition);
        verify(level, never()).getBlockEntity(any());
    }

    @Test void reciprocalSavedLinkRebuildsPhysicsWhenThePeerLoads(){
        Level level = mock(Level.class);
        DockingConnectorBlockEntity ship = endpoint(level, new BlockPos(0, 10, 0));
        DockingConnectorBlockEntity dock = endpoint(level, new BlockPos(32, 10, 0));
        ship.otherConnectorPosition = dock.getBlockPos();
        dock.otherConnectorPosition = ship.getBlockPos();
        when(level.getBlockEntity(dock.getBlockPos())).thenReturn(dock);
        DockingConnectorPair pair = mock(DockingConnectorPair.class);
        MagnetMap<DockingConnectorBlockEntity> prev = DockingConnectorBlockEntity.MAGNET_CONTROLLER;
        MagnetMap<DockingConnectorBlockEntity> controller = mock(MagnetMap.class);
        DockingConnectorBlockEntity.MAGNET_CONTROLLER = controller;
        try{
            when(controller.getPair(level, ship.getBlockPos(), dock.getBlockPos())).thenReturn(pair);
            doAnswer(call -> {
                when(((SimulatedDockingApi.PhysicsState)ship).hasDockingConstraint()).thenReturn(true);
                return null;
            }).when(pair).dock(true);
            assertFalse(SimulatedDockingApi.restoreConnection(ship));
            verify(pair, never()).dock(true);
            when(level.hasChunkAt(dock.getBlockPos())).thenReturn(true);
            assertFalse(SimulatedDockingApi.hasPhysicalConnection(ship));
            assertTrue(SimulatedDockingApi.restoreConnection(ship));
            assertTrue(SimulatedDockingApi.restoreConnection(ship));
            verify(pair, times(1)).dock(true);
        }finally{
            DockingConnectorBlockEntity.MAGNET_CONTROLLER = prev;
        }
    }

    @Test void aQueuedNativeConstraintIsNotRecreatedBeforeThePhysicsTick(){
        Level level = mock(Level.class);
        DockingConnectorBlockEntity ship = endpoint(level, new BlockPos(0, 10, 0));
        DockingConnectorBlockEntity dock = endpoint(level, new BlockPos(32, 10, 0));
        ship.otherConnectorPosition = dock.getBlockPos();
        dock.otherConnectorPosition = ship.getBlockPos();
        when(level.hasChunkAt(dock.getBlockPos())).thenReturn(true);
        when(level.getBlockEntity(dock.getBlockPos())).thenReturn(dock);
        when(((SimulatedDockingApi.PhysicsState)ship).isDockingConstraintPending()).thenReturn(true);
        assertFalse(SimulatedDockingApi.restoreConnection(ship));
        assertFalse(SimulatedDockingApi.hasPhysicalConnection(ship));
        when(((SimulatedDockingApi.PhysicsState)ship).hasDockingConstraint()).thenReturn(true);
        assertTrue(SimulatedDockingApi.restoreConnection(ship));
    }

    @Test void loadedUnrelatedEndpointCannotBeForceDocked(){
        Level level = mock(Level.class);
        DockingConnectorBlockEntity ship = endpoint(level, new BlockPos(0, 10, 0));
        DockingConnectorBlockEntity dock = endpoint(level, new BlockPos(32, 10, 0));
        ship.otherConnectorPosition = dock.getBlockPos();
        when(level.hasChunkAt(dock.getBlockPos())).thenReturn(true);
        when(level.getBlockEntity(dock.getBlockPos())).thenReturn(dock);
        assertFalse(SimulatedDockingApi.restoreConnection(ship));
        assertFalse(SimulatedDockingApi.hasPhysicalConnection(ship));
    }

    private static DockingConnectorBlockEntity endpoint(Level level, BlockPos pos){
        var endpoint = mock(DockingConnectorBlockEntity.class,
                withSettings().extraInterfaces(SimulatedDockingApi.PhysicsState.class));
        when(endpoint.getLevel()).thenReturn(level);
        when(endpoint.getBlockPos()).thenReturn(pos);
        return endpoint;
    }
}
