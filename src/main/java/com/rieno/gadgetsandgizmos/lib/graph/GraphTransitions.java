package com.rieno.gadgetsandgizmos.lib.graph;

// Evaluate signal edges and bounded numeric transitions without owning host state
public final class GraphTransitions{
    // Keep transition operations stateless
    private GraphTransitions(){}

    // Fire only when a condition changes from false to true
    public static boolean risingEdge(boolean prev, boolean current){
        return !prev && current;
    }

    // Move one finite step toward the target without passing it
    public static double approach(double current, double target, double step){
        double val = Double.isFinite(current) ? current : 0;
        if(!Double.isFinite(target) || !Double.isFinite(step)) return val;
        double amount = Math.abs(step);
        return val < target ? Math.min(target, val + amount) : Math.max(target, val - amount);
    }

    // Blend finite endpoints using progress clamped between zero and one
    public static double interpolate(double start, double target, double progress){
        double from = Double.isFinite(start) ? start : 0;
        double to = Double.isFinite(target) ? target : from;
        double amount = Double.isFinite(progress) ? Math.max(0, Math.min(1, progress)) : 0;
        if(amount == 0) return from;
        if(amount == 1) return to;
        return (1 - amount) * from + amount * to;
    }
}
