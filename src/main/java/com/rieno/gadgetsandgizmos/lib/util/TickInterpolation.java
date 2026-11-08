package com.rieno.gadgetsandgizmos.lib.util;

// Interpolate one received update without replaying it on later ticks
public final class TickInterpolation{
    // Retain the endpoints and local tick of the latest changing update
    private double prev, current;
    private long updateTick;
    private boolean initialized;

    // Replace the baseline when a host loads or changes its connection
    public void reset(double value, long tick){
        if(!Double.isFinite(value)) throw new IllegalArgumentException("A finite animation value is required");
        prev = current = value;
        updateTick = tick;
        initialized = true;
    }

    // Start one tick of motion, retaining the baseline for duplicate updates
    public void update(double value, long tick, boolean discontinuity){
        if(!Double.isFinite(value)) throw new IllegalArgumentException("A finite animation value is required");
        if(!initialized || discontinuity || tick < updateTick){
            reset(value, tick);
            return;
        }
        if(Double.compare(value, current) == 0) return;
        prev = current;
        current = value;
        updateTick = tick;
    }

    // Hold the final value once the receiving tick has elapsed
    public double sample(long tick, float partialTicks){
        if(tick != updateTick) return current;
        double fraction = Float.isFinite(partialTicks) ? Math.max(0, Math.min(1, partialTicks)) : 0;
        return prev + (current - prev) * fraction;
    }
}
