package com.rieno.gadgetsandgizmos.lib.inventory;

import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import java.util.List;

// Reuse a local capability description while all three physical handler identities remain unchanged
public final class LocalResourcePool{
    private IItemHandler items;
    private IFluidHandler fluids;
    private IEnergyStorage energy;
    private ResourceRouting.Pool pool = ResourceRouting.Pool.EMPTY;

    // Read the current handlers on the owning thread; resource contents are never cached
    public ResourceRouting.Pool get(IItemHandler items, IFluidHandler fluids, IEnergyStorage energy){
        if(this.items == items && this.fluids == fluids && this.energy == energy) return pool;
        this.items = items;
        this.fluids = fluids;
        this.energy = energy;
        pool = new ResourceRouting.Pool(items == null ? List.of() : List.of(items),
                fluids == null ? List.of() : List.of(fluids), energy == null ? List.of() : List.of(energy));
        return pool;
    }
}
