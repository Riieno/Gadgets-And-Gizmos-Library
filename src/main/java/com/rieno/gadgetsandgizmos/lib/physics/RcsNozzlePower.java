package com.rieno.gadgetsandgizmos.lib.physics;

/** Resolve available thrust for shaft, pressure, and self-powered RCS nozzles. */
public final class RcsNozzlePower {
    private RcsNozzlePower() {}

    public static double fromShaft(double rpm, double fullThrust, double fullPowerRpm) {
        if (!Double.isFinite(rpm) || !Double.isFinite(fullThrust) || fullThrust <= 0.0D
                || !Double.isFinite(fullPowerRpm) || fullPowerRpm <= 0.0D) return 0.0D;
        return fullThrust * Math.min(1.0D, Math.abs(rpm) / fullPowerRpm);
    }

    public static double available(boolean selfPowered, boolean pressureTankConnected,
                                   boolean pressureTankSupplied, double rpm,
                                   double fullThrust, double fullPowerRpm) {
        if (!Double.isFinite(fullThrust) || fullThrust <= 0.0D) return 0.0D;
        if (selfPowered) return fullThrust;
        if (pressureTankConnected) return pressureTankSupplied ? fullThrust : 0.0D;
        return fromShaft(rpm, fullThrust, fullPowerRpm);
    }
}
