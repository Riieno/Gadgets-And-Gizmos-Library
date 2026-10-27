package com.rieno.gadgetsandgizmos.lib.power;

// Share the FE-per-watt-per-tick convention used by electrical device adaptors
public final class EnergyUnitConversion{
    private EnergyUnitConversion(){}

    // Round received energy down and supplied energy up so conversion cannot generate FE
    public static long transfer(LongEnergyStorage storage, double watts, double fePerWatt, long limit){
        if(!Double.isFinite(watts) || !Double.isFinite(fePerWatt) || fePerWatt <= 0 || limit <= 0) return 0;
        if(watts >= 0 ? !storage.canReceive() : !storage.canExtract()) return 0;
        double amount = Math.abs(watts) * fePerWatt;
        long requested = (long)Math.min(limit, watts >= 0 ? Math.floor(amount) : Math.ceil(amount));
        return watts >= 0 ? storage.receiveEnergy(requested, false) : -storage.extractEnergy(requested, false);
    }

    public static double wattHours(long energy, double fePerWatt){
        return Double.isFinite(fePerWatt) && fePerWatt > 0 ? energy / (fePerWatt * 20D * 3600D) : 0;
    }
}
