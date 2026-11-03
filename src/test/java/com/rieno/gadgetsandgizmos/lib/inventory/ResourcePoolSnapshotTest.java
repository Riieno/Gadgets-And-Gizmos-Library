package com.rieno.gadgetsandgizmos.lib.inventory;

import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.energy.EnergyStorage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResourcePoolSnapshotTest{
    @Test void sharedHandlersAreCountedOnceAndTotalsExceedIntegerRange(){
        var a = new EnergyStorage(Integer.MAX_VALUE);
        var b = new EnergyStorage(Integer.MAX_VALUE);
        a.receiveEnergy(Integer.MAX_VALUE, false);
        b.receiveEnergy(Integer.MAX_VALUE, false);
        var pool = new ResourceRouting.Pool(List.of(), List.of(), List.of(a, a, b));
        var data = ContainerNetworkSnapshot.capturePool(pool, "", "", 0, 32);
        var rows = data.getList("Rows", Tag.TAG_COMPOUND);
        assertEquals(1, rows.size());
        assertEquals(2L * Integer.MAX_VALUE, rows.getCompound(0).getLong("Amount"));
        assertEquals(2L * Integer.MAX_VALUE, rows.getCompound(0).getLong("Capacity"));
        assertEquals(2, data.getInt("Handlers"));
        assertEquals(Integer.MAX_VALUE, a.getEnergyStored());
    }

    @Test void filtersUseResourceIdsAndNamesWithoutChangingStorage(){
        var storage = new EnergyStorage(1000);
        storage.receiveEnergy(400, false);
        var pool = new ResourceRouting.Pool(List.of(), List.of(), List.of(storage));
        assertEquals(1, ContainerNetworkSnapshot.capturePool(pool, "energy", " NEOFORGE:ENERGY ", 0, 32).getInt("Total"));
        assertEquals(0, ContainerNetworkSnapshot.capturePool(pool, "item", "", 0, 32).getInt("Total"));
        assertEquals(0, ContainerNetworkSnapshot.capturePool(pool, "", "diesel", 0, 32).getInt("Total"));
        assertEquals(400, storage.getEnergyStored());
    }

    @Test void emptyPoolsAndOversizedOffsetsStayBounded(){
        var empty = ContainerNetworkSnapshot.capturePool(ResourceRouting.Pool.EMPTY, "", "", Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertEquals(0, empty.getInt("Total"));
        assertEquals(0, empty.getInt("Offset"));
        assertTrue(empty.getList("Rows", Tag.TAG_COMPOUND).isEmpty());
        var pool = new ResourceRouting.Pool(List.of(), List.of(), List.of(new EnergyStorage(1000)));
        var data = ContainerNetworkSnapshot.capturePool(pool, "", "", Integer.MAX_VALUE, 0);
        assertEquals(0, data.getInt("Offset"));
        assertEquals(1, data.getList("Rows", Tag.TAG_COMPOUND).size());
    }
}
