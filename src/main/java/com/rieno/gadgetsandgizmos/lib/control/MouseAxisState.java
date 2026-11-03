package com.rieno.gadgetsandgizmos.lib.control;

// Share bounded axis accumulation and inactivity expiry between client and server
public final class MouseAxisState{
    // Keep the accumulator and movement timestamp in the same owner
    private double value;
    private long lastMovement;
    private boolean sampled;

    // Read the current accumulator
    public double value(){ return value; }

    // Replace one finite movement sample
    public void sample(double val, long tick){
        if(!Double.isFinite(val)) return;
        value = Math.clamp(val, -1, 1);
        lastMovement = tick;
        sampled = true;
    }

    // Start the next movement at zero after the configured idle period
    public boolean expire(long tick, int timeout){
        if(!sampled || tick >= lastMovement && tick - lastMovement < Math.max(1, timeout)) return false;
        boolean changed = value != 0;
        value = 0;
        sampled = false;
        return changed;
    }
}
