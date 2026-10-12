package com.rieno.gadgetsandgizmos.lib.graph;

import java.util.List;

// Advance retained counters without depending on host graph state or node identifiers
public final class GraphCounters{
    private GraphCounters(){}

    // Convert a numeric limit into a non-negative integer count
    public static int wholeCount(double val){
        if(!Double.isFinite(val) || val <= 0) return 0;
        return (int) Math.min(Integer.MAX_VALUE, val);
    }

    // Pass at most the requested number of pulses, restarting on a reset signal
    public static LimitedPulse limitedPulse(int count, int limit, boolean reset){
        int current = reset ? 0 : Math.max(0, count);
        boolean passed = current < Math.max(0, limit);
        return new LimitedPulse(passed ? current + 1 : current, passed);
    }

    // Apply one numeric step while keeping invalid values out of retained state
    public static double step(double value, double amount, Direction direction){
        double current = Double.isFinite(value) ? value : 0;
        if(!Double.isFinite(amount)) return current;
        double next = current + (direction == Direction.DECREMENT ? -amount : amount);
        return Double.isFinite(next) ? next : current;
    }

    // Reset takes priority over applying a step on the current pulse
    public static double step(double value, double amount, Direction direction, boolean reset){
        return reset ? 0 : step(value, amount, direction);
    }

    public record LimitedPulse(int count, boolean passed){}

    public enum Direction{
        INCREMENT("increment"), DECREMENT("decrement");
        private final String id;
        Direction(String id){ this.id = id; }
        public String id(){ return id; }
        public static Direction fromId(String id){ return "decrement".equalsIgnoreCase(id) ? DECREMENT : INCREMENT; }
        public static List<String> optionIds(){ return List.of("increment", "decrement"); }
    }
}
