package com.rieno.gadgetsandgizmos.lib.power.powergrid;

import com.rieno.gadgetsandgizmos.lib.compat.MixinCompatibility;

// Keep optional electrical classes out of ordinary graph and unit conversion loading
public final class PowerGridUnits{
    private PowerGridUnits(){}

    public static boolean available(){ return MixinCompatibility.isModLoaded("powergrid"); }

    public static double fePerWatt(){
        return available() ? org.patryk3211.powergrid.electricity.febridge.IFEBridgeHandler.wattToFE() : Double.NaN;
    }
}
