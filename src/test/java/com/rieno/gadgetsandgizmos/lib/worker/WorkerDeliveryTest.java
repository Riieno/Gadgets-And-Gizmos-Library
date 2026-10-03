package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkerDeliveryTest{
    // Leave three crafted items in custody after satisfying a one-item request
    @Test void splitsRequestedAmountWithoutLosingComponents(){
        CompoundTag data = new CompoundTag();
        data.putString("Custom", "kept");
        var cargo = new WorkerResourcePacket(WorkerResourceKey.energy(), 4, data);
        var delivered = WorkerDelivery.requested(cargo, 1);
        assertEquals(1, delivered.amount());
        assertEquals(3, cargo.remainderAfter(delivered.amount()).amount());
        assertEquals(data, delivered.payload());
        assertEquals(4, cargo.amount());
    }

    // Prefer a matching container before a closer general-purpose container
    @Test void prefersFilteredOrStockedStorage(){
        var cargo = new WorkerResourcePacket(WorkerResourceKey.energy(), 3, null);
        var nearby = endpoint(cargo, 1, 1);
        var filtered = endpoint(cargo, 30, 0);
        assertSame(filtered, WorkerDelivery.storage(List.of(nearby, filtered), cargo, Vec3.ZERO));
        when(filtered.insert(cargo, true)).thenReturn(0L);
        assertSame(nearby, WorkerDelivery.storage(List.of(nearby, filtered), cargo, Vec3.ZERO));
    }

    // Do not route surplus into a virtual crafting station or an unloaded endpoint
    @Test void excludesStationsAndUnavailableContainers(){
        var cargo = new WorkerResourcePacket(WorkerResourceKey.energy(), 3, null);
        var station = endpoint(cargo, 1, 0);
        var unloaded = endpoint(cargo, 2, 0);
        var storage = endpoint(cargo, 3, 1);
        when(station.acceptsDelivery()).thenReturn(false);
        when(unloaded.isAvailable()).thenReturn(false);
        assertSame(storage, WorkerDelivery.storage(List.of(station, unloaded, storage), cargo, Vec3.ZERO));
    }

    // Use the smaller effective view distance and retain a chunk-edge margin
    @Test void usesEffectiveViewDistance(){
        assertEquals(160.0D, WorkerDeliveryTravel.viewBoundary(8, 16));
        assertEquals(160.0D, WorkerDeliveryTravel.viewBoundary(16, 8));
    }

    // Restore a distant return without serializing navigation or live entity references
    @Test void travelRoundTripsThroughNbt(){
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Recipient", java.util.UUID.randomUUID());
        tag.putDouble("HomeX", 1.5D);
        tag.putDouble("HomeY", 64.0D);
        tag.putDouble("HomeZ", 3.5D);
        tag.putBoolean("Outbound", true);
        tag.putBoolean("Returning", true);
        assertEquals(tag, WorkerDeliveryTravel.fromTag(tag).toTag());
    }

    private static WorkerEndpoint endpoint(WorkerResourcePacket cargo, int x, int priority){
        WorkerEndpoint endpoint = mock(WorkerEndpoint.class);
        when(endpoint.isAvailable()).thenReturn(true);
        when(endpoint.acceptsDelivery()).thenReturn(true);
        when(endpoint.canInsert(cargo.resource())).thenReturn(true);
        when(endpoint.insert(cargo, true)).thenReturn(cargo.amount());
        when(endpoint.insertionPriority(cargo.resource())).thenReturn(priority);
        when(endpoint.position()).thenReturn(new BlockPos(x, 64, 0));
        return endpoint;
    }
}
