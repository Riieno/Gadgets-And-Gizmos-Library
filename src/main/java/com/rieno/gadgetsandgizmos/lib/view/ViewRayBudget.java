package com.rieno.gadgetsandgizmos.lib.view;

// Spread a ray rate across game ticks without bursts after unloaded time
public final class ViewRayBudget{
    public static final int TICKS_PER_SECOND = 20;
    public static final int MAX_PER_SECOND = ViewRaycast.MAX_RAYS * TICKS_PER_SECOND;
    private long tick = Long.MIN_VALUE;
    private double remainder;

    // Allocate this tick's share once, retaining fractional rays for later ticks
    public int next(long tick, double rate){
        if(this.tick == tick) return 0;
        if(this.tick != tick - 1) remainder = 0;
        this.tick = tick;
        double val = Double.isFinite(rate) ? Math.clamp(rate, 0, MAX_PER_SECOND) : 0;
        if(val == 0){ remainder = 0; return 0; }
        remainder += val / TICKS_PER_SECOND;
        int count = (int) Math.floor(remainder + 1.0E-9D);
        remainder = Math.max(0, remainder - count);
        return count;
    }
}
