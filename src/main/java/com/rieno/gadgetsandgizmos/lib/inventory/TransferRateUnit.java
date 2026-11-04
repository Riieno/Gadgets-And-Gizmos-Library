package com.rieno.gadgetsandgizmos.lib.inventory;

// Convert a per-tick reading using Minecraft's nominal twenty ticks per second
public enum TransferRateUnit{
    TICK("t", 1), SECOND("s", 20), MINUTE("min", 1200), HOUR("h", 72000);

    private final String suffix;
    private final long ticks;

    TransferRateUnit(String suffix, long ticks){ this.suffix = suffix; this.ticks = ticks; }
    public String suffix(){ return suffix; }
    public double convert(double perTick){ return perTick * ticks; }
}
