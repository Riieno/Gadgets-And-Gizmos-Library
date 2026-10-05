package com.rieno.gadgetsandgizmos.lib.scm;

// Resolve usable thrust from the environment and a provider's reported efficiency
public final class ScmPropulsionAvailability{
    private ScmPropulsionAvailability(){}

    // Preserve full output when a provider does not report efficiency
    public static double capacity(double maximumThrust, double airPressure,
                                  boolean pressureIndependent, double efficiency){
        double maximum = Double.isFinite(maximumThrust) ? Math.max(0.0D, maximumThrust) : 0.0D;
        double pressure = pressureIndependent ? 1.0D
                : Double.isFinite(airPressure) ? Math.max(0.0D, airPressure) : 1.0D;
        double usable = Double.isFinite(efficiency)
                ? Math.max(0.0D, Math.min(1.0D, efficiency)) : 1.0D;
        double result = maximum * pressure * usable;
        return Double.isFinite(result) ? result : 0.0D;
    }
}
