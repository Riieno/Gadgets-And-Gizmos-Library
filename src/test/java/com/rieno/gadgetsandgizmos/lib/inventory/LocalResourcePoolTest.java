package com.rieno.gadgetsandgizmos.lib.inventory;

import net.neoforged.neoforge.energy.EnergyStorage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocalResourcePoolTest{
    @Test
    void retainedHandlersAlwaysExposeCurrentEnergy(){
        var cache = new LocalResourcePool();
        var energy = new EnergyStorage(1000);
        var pool = cache.get(null, null, energy);
        energy.receiveEnergy(50, false);
        assertSame(pool, cache.get(null, null, energy));
        assertEquals(50, pool.energy().getFirst().getEnergyStored());
        energy.extractEnergy(20, false);
        assertEquals(30, pool.energy().getFirst().getEnergyStored());
    }

    @Test
    void replacedAndRemovedHandlersInvalidateTheDescriptionImmediately(){
        var cache = new LocalResourcePool();
        var first = new EnergyStorage(100);
        var second = new EnergyStorage(200);
        var old = cache.get(null, null, first);
        var next = cache.get(null, null, second);
        assertNotSame(old, next);
        assertSame(second, next.energy().getFirst());
        assertTrue(cache.get(null, null, null).isEmpty());
        assertSame(first, old.energy().getFirst());
    }
}
