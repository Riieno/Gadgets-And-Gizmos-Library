package com.rieno.gadgetsandgizmos.lib.kinetics;

/**
 * Limits a driven output to a requested RPM without increasing its input speed.
 */
public final class KineticTargetSpeed {
    private KineticTargetSpeed() {
    }

    public static float outputRatio(float inputRpm, double targetRpm) {
        if (Double.isNaN(targetRpm)) {
            return 1.0f;
        }
        if (!Float.isFinite(inputRpm) || Math.abs(inputRpm) < 1.0E-4f) {
            return 0.0f;
        }
        if (!Double.isFinite(targetRpm)) {
            return 0.0f;
        }
        return (float) Math.min(1.0D,
                Math.max(0.0D, targetRpm) / Math.abs(inputRpm));
    }
}
