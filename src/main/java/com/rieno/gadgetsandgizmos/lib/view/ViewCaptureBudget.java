package com.rieno.gadgetsandgizmos.lib.view;

// Bound aggregate capture work independently of the main view's frame rate
public final class ViewCaptureBudget{
    private final long minimumInterval;
    private final double workloadFraction;
    private double averageCost;
    private long nextAllowed;

    // Set the maximum refresh rate and the fraction of time available to captures
    public ViewCaptureBudget(double refreshRate, double workloadFraction){
        if(!Double.isFinite(refreshRate) || refreshRate <= 0
                || !Double.isFinite(workloadFraction) || workloadFraction <= 0 || workloadFraction >= 1){
            throw new IllegalArgumentException("Capture rate and workload fraction must be finite and positive");
        }
        minimumInterval = Math.max(1, (long) (1_000_000_000D / refreshRate));
        this.workloadFraction = workloadFraction;
    }
    // Check whether another source may use the shared render budget
    public boolean ready(long now){ return now >= nextAllowed; }
    // Account for CPU or asynchronously measured GPU work without blocking the renderer
    public void completed(long end, long duration){
        long cost = Math.max(0, duration);
        averageCost = averageCost == 0 ? cost : averageCost * 0.75D + cost * 0.25D;
        double retainedCost = Math.max(cost, averageCost);
        long idle = (long) Math.ceil(Math.max(minimumInterval - cost,
                retainedCost * (1.0D / workloadFraction - 1.0D)));
        nextAllowed = end > Long.MAX_VALUE - idle ? Long.MAX_VALUE : end + idle;
    }
    // Forget timing measurements when the owning world changes
    public void reset(){ averageCost = 0; nextAllowed = 0; }
}
