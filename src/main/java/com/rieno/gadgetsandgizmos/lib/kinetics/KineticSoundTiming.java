package com.rieno.gadgetsandgizmos.lib.kinetics;

// Calculate kinetic sound intervals from measured rotation samples
public final class KineticSoundTiming {
    private static final float TICKS_PER_SECOND = 20.0f;

    // Initialize the kinetic sound timing helper
    private KineticSoundTiming() {
    }

    // Fit a reciprocal delay curve through two measured speed samples
    public static int fittedReciprocalDelayTicks(float rpm,
                                                 float firstRpm, float firstDelaySeconds,
                                                 float secondRpm, float secondDelaySeconds) {
        requirePositiveFinite(rpm, "rpm");
        requirePositiveFinite(firstRpm, "firstRpm");
        requirePositiveFinite(firstDelaySeconds, "firstDelaySeconds");
        requirePositiveFinite(secondRpm, "secondRpm");
        requirePositiveFinite(secondDelaySeconds, "secondDelaySeconds");
        if (firstRpm == secondRpm || firstDelaySeconds == secondDelaySeconds) {
            throw new IllegalArgumentException("Kinetic sound timing samples must be distinct");
        }

        double rpmOffset = (secondDelaySeconds * secondRpm - firstDelaySeconds * firstRpm)
                / (firstDelaySeconds - secondDelaySeconds);
        double scale = firstDelaySeconds * (firstRpm + rpmOffset);
        double divisor = rpm + rpmOffset;
        if (!Double.isFinite(scale) || scale <= 0.0D || !Double.isFinite(divisor) || divisor <= 0.0D) {
            throw new IllegalArgumentException("Kinetic sound timing samples do not produce a positive curve");
        }

        double ticks = scale / divisor * TICKS_PER_SECOND;
        if (ticks >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return Math.max((int) Math.round(ticks), 0);
    }

    // Require one positive finite timing value
    private static void requirePositiveFinite(float value, String name) {
        if (!Float.isFinite(value) || value <= 0.0f) {
            throw new IllegalArgumentException(name + " must be positive and finite");
        }
    }
}
