package com.rieno.gadgetsandgizmos.lib.inventory;

import org.junit.jupiter.api.Test;
import net.neoforged.neoforge.energy.EnergyStorage;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ResourceRoutingTest{
    // A full reserve still has work to do when an engine buffer can accept fuel
    @Test
    void pendingFuelTransferIsSimulatedWithoutChangingEitherPool(){
        var reserve = new EnergyStorage(1000);
        var engine = new EnergyStorage(4000);
        reserve.receiveEnergy(1000, false);
        var source = new ResourceRouting.Pool(List.of(), List.of(), List.of(reserve));
        var target = new ResourceRouting.Pool(List.of(), List.of(), List.of(engine));
        assertTrue(ResourceRouting.canMove(source, target, stack -> true, stack -> true));
        assertEquals(1000, reserve.getEnergyStored());
        assertEquals(0, engine.getEnergyStored());
        engine.receiveEnergy(4000, false);
        assertFalse(ResourceRouting.canMove(source, target, stack -> true, stack -> true));
        assertFalse(ResourceRouting.canMove(source, source, stack -> true, stack -> true));
        assertFalse(ResourceRouting.canMove(ResourceRouting.Pool.EMPTY, target, stack -> true, stack -> true));
    }

    @Test
    void emptyAndSingletonPoolsDoNotCreateAdditionalStorage(){
        assertSame(ResourceRouting.Pool.EMPTY, ResourceRouting.combine(List.of()));
        assertSame(ResourceRouting.Pool.EMPTY, ResourceRouting.combine(List.of(ResourceRouting.Pool.EMPTY)));
        assertEquals(List.of(), ResourceRouting.distinct(List.of()));
        assertEquals(List.of(), ResourceRouting.distinct(Arrays.asList((Object)null)));
        assertThrows(NullPointerException.class, () -> ResourceRouting.combine(Arrays.asList((ResourceRouting.Pool)null)));
    }

    @Test
    void deduplicationUsesIdentityAndKeepsTheOriginalOrder(){
        String first = new String("same");
        String second = new String("same");
        var res = ResourceRouting.distinct(Arrays.asList(null, first, first, second, null, first));
        assertEquals(2, res.size());
        assertSame(first, res.get(0));
        assertSame(second, res.get(1));
        assertSame(first, ResourceRouting.distinct(List.of(first)).getFirst());
        assertThrows(UnsupportedOperationException.class, () -> res.add("other"));
    }
}
