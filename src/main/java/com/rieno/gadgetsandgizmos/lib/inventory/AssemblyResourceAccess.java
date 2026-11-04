package com.rieno.gadgetsandgizmos.lib.inventory;

import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

// Mark assembly bridges so resource discovery cannot route back through another bridge
public interface AssemblyResourceAccess{
    IFluidHandler getAssemblyFluidHandler();
    IEnergyStorage getAssemblyEnergyHandler();
}
