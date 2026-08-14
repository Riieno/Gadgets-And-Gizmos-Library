package com.rieno.gadgetsandgizmos.lib.power.alternator;

// Convert kinetic speed and tuning into alternator output and stress values
public final class AlternatorKinetics {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the alternator kinetics
    private AlternatorKinetics() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the effective RPM
    public static double effectiveRpm(double speed, AlternatorTuning tuning) {
        double rpm = Math.abs(speed);
        double minRpm = Math.max(1.0D, tuning.minRpm());
        if (rpm < minRpm) {
            return 0.0D;
        }
        double ratedRpm = Math.max(minRpm, tuning.ratedRpm());
        return clamp(rpm, minRpm, ratedRpm);
    }

    // Calculate FE generated per tick
    public static int generatedFePerTick(double speed, AlternatorTuning tuning) {
        double rpm = effectiveRpm(speed, tuning);
        if (rpm <= 0.0D) {
            return 0;
        }
        double ratedRpm = Math.max(Math.max(1.0D, tuning.minRpm()), tuning.ratedRpm());
        double normalized = clamp(rpm / ratedRpm, 0.0D, 1.0D);
        return (int) Math.floor(Math.max(1, tuning.maxFePerTick()) * normalized);
    }

    // Get the stress base rate per RPM
    public static float stressBaseRatePerRpm(AlternatorTuning tuning) {
        double ratedRpm = Math.max(1.0D, tuning.ratedRpm());
        double maxStress = Math.max(1.0D, tuning.maxStressImpact());
        return (float) (maxStress / ratedRpm);
    }

    // Get the stress at speed
    public static float stressAtSpeed(double speed, AlternatorTuning tuning) {
        return stressBaseRatePerRpm(tuning) * (float) Math.abs(speed);
    }

    // Clamp the alternator kinetics
    private static double clamp(double val, double min, double max) {
        if (val < min) {
            return min;
        }
        if (val > max) {
            return max;
        }
        return val;
    }
}
