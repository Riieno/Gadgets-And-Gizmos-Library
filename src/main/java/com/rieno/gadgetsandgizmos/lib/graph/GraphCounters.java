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

    // Accept a boolean direction input without changing the serialized direction aliases
    public static double step(double value, double amount, boolean decrement, boolean reset){
        return step(value, amount, decrement ? Direction.DECREMENT : Direction.INCREMENT, reset);
    }

    public record LimitedPulse(int count, boolean passed){}

    public enum FlipMode{
        EVERY_PULSE("Every Pulse"), EVERY_SECOND_PULSE("Every Second Pulse"), EVERY_NTH_PULSE("Every Nth Pulse");

        private final String label;
        FlipMode(String label){ this.label = label; }
        public String label(){ return label; }
        public static List<String> optionLabels(){ return java.util.Arrays.stream(values()).map(FlipMode::label).toList(); }
        public static FlipMode fromId(String value){
            String id = value == null ? "" : value.trim().replace(' ', '_');
            for(FlipMode mode : values()) if(mode.name().equalsIgnoreCase(id)) return mode;
            return EVERY_PULSE;
        }
    }

    public record FlipPulse(boolean value, int skipped, boolean flipped){}

    // The custom count specifies pulses to skip before the next toggle
    public static FlipPulse flipPulse(boolean value, int skipped, FlipMode mode, double requestedSkip){
        int limit = switch(mode){
            case EVERY_PULSE -> 0;
            case EVERY_SECOND_PULSE -> 1;
            case EVERY_NTH_PULSE -> wholeCount(requestedSkip);
        };
        if(Math.max(0, skipped) >= limit) return new FlipPulse(!value, 0, true);
        return new FlipPulse(value, Math.max(0, skipped) + 1, false);
    }

    public enum Direction{
        INCREMENT("increment"), DECREMENT("decrement");
        private final String id;
        Direction(String id){ this.id = id; }
        public String id(){ return id; }
        public static Direction fromId(String id){ return "decrement".equalsIgnoreCase(id) ? DECREMENT : INCREMENT; }
        public static List<String> optionIds(){ return List.of("increment", "decrement"); }
    }
}
