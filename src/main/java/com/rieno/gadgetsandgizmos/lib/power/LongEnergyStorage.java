package com.rieno.gadgetsandgizmos.lib.power;

// Expose FE storage whose total capacity exceeds NeoForge's integer capability range
public interface LongEnergyStorage {
    // Receive FE and return the accepted amount
    long receiveEnergy(long maximum, boolean simulate);

    // Extract FE and return the removed amount
    long extractEnergy(long maximum, boolean simulate);

    // Get the stored FE
    long getEnergyStored();

    // Get the FE capacity
    long getMaxEnergyStored();

    // Check whether FE may be extracted
    boolean canExtract();

    // Check whether FE may be received
    boolean canReceive();
}
