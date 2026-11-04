package com.rieno.gadgetsandgizmos.lib.inventory;

import net.neoforged.neoforge.fluids.FluidStack;

// Apply the entry block's selected output to matching fluid storage throughout its automation network
public interface FluidExtractionPolicy{
    boolean allowsFluidExtraction(FluidStack stack);
}
