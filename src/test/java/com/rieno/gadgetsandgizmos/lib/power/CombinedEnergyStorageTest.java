package com.rieno.gadgetsandgizmos.lib.power;

import net.neoforged.neoforge.energy.EnergyStorage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Keep pooled electrical accounting exact across shared, capped and disappearing stores
class CombinedEnergyStorageTest{
    @Test void exactTransfersCrossIntegerLimitsWithoutCountingSharedStoresTwice(){
        var first = new Store(3_000_000_000L, 4_000_000_000L);
        var second = new Store(2_000_000_000L, 4_000_000_000L);
        var pool = new CombinedEnergyStorage(() -> List.of(first, first, second));
        assertEquals(5_000_000_000L, pool.getEnergyStored());
        assertEquals(8_000_000_000L, pool.getMaxEnergyStored());
        assertEquals(4_000_000_000L, pool.extractEnergy(4_000_000_000L, true));
        assertEquals(5_000_000_000L, pool.getEnergyStored());
        assertEquals(4_000_000_000L, pool.extractEnergy(4_000_000_000L, false));
        assertEquals(0, first.stored); assertEquals(1_000_000_000L, second.stored);
        assertEquals(7_000_000_000L, pool.receiveEnergy(Long.MAX_VALUE, true));
        assertEquals(1_000_000_000L, pool.getEnergyStored());
        assertEquals(7_000_000_000L, pool.receiveEnergy(Long.MAX_VALUE, false));
        assertEquals(8_000_000_000L, pool.getEnergyStored());
    }

    @Test void standardAdaptersRetainLimitsAndSharedCapabilityIdentity(){
        var standard = new EnergyStorage(10000, 1000, 500, 2000);
        var pool = new CombinedEnergyStorage(() -> List.of(CombinedEnergyStorage.adapt(standard), CombinedEnergyStorage.adapt(standard)));
        assertEquals(2000, pool.getEnergyStored());
        assertEquals(10000, pool.getMaxEnergyStored());
        assertEquals(500, pool.extractEnergy(Long.MAX_VALUE, true));
        assertEquals(2000, standard.getEnergyStored());
        assertEquals(500, pool.extractEnergy(Long.MAX_VALUE, false));
        assertEquals(1500, standard.getEnergyStored());
        assertEquals(1000, pool.receiveEnergy(Long.MAX_VALUE, false));
        assertEquals(2500, standard.getEnergyStored());
        assertEquals(0, pool.extractEnergy(-1, false));
    }

    @Test void liveMembershipAndPermissionsAreUsedForEveryOperation(){
        var first = new Store(2000, 5000); var second = new Store(3000, 5000);
        List<LongEnergyStorage> members = new ArrayList<>(List.of(first, second));
        var pool = new CombinedEnergyStorage(() -> members);
        first.extract = false; second.receive = false;
        assertEquals(3000, pool.extractEnergy(5000, false));
        assertEquals(3000, pool.receiveEnergy(5000, true));
        assertEquals(2000, first.stored);
        members.remove(second);
        assertFalse(pool.canExtract());
        assertEquals(0, pool.extractEnergy(1000, false));
        assertEquals(2000, pool.getEnergyStored());
        members.clear();
        assertEquals(0, pool.getMaxEnergyStored()); assertFalse(pool.canReceive());
    }

    @Test void aggregateTotalsSaturateInsteadOfOverflowing(){
        var pool = new CombinedEnergyStorage(() -> List.of(new Store(Long.MAX_VALUE - 4, Long.MAX_VALUE), new Store(10, 10)));
        assertEquals(Long.MAX_VALUE, pool.getEnergyStored());
        assertEquals(Long.MAX_VALUE, pool.getMaxEnergyStored());
        assertEquals(0, pool.receiveEnergy(0, false));
    }

    private static final class Store implements LongEnergyStorage{
        private long stored;
        private final long capacity;
        private boolean extract = true, receive = true;
        private Store(long stored, long capacity){ this.stored = stored; this.capacity = capacity; }
        @Override public long receiveEnergy(long amount, boolean simulate){ long val = Math.min(amount, capacity - stored); if(!simulate) stored += val; return val; }
        @Override public long extractEnergy(long amount, boolean simulate){ long val = Math.min(amount, stored); if(!simulate) stored -= val; return val; }
        @Override public long getEnergyStored(){ return stored; }
        @Override public long getMaxEnergyStored(){ return capacity; }
        @Override public boolean canExtract(){ return extract; }
        @Override public boolean canReceive(){ return receive; }
    }
}
