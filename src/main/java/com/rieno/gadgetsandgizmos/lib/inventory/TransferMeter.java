package com.rieno.gadgetsandgizmos.lib.inventory;

import java.util.Arrays;
import java.util.function.LongSupplier;

// Measure accepted transfers over the last twenty completed game ticks
public final class TransferMeter{
    public static final int WINDOW_TICKS = 20;
    private final LongSupplier clock;
    private final long[] ticks = new long[WINDOW_TICKS + 1];
    private final long[] inputs = new long[WINDOW_TICKS + 1];
    private final long[] outputs = new long[WINDOW_TICKS + 1];

    public TransferMeter(LongSupplier clock){
        this.clock = clock;
        Arrays.fill(ticks, Long.MIN_VALUE);
    }

    // Record executed storage mutations; callers exclude simulations and internal redistribution
    public void record(long input, long output){
        if(input <= 0 && output <= 0) return;
        long tick = clock.getAsLong();
        int idx = Math.floorMod(tick, ticks.length);
        if(ticks[idx] != tick){
            ticks[idx] = tick;
            inputs[idx] = 0;
            outputs[idx] = 0;
        }
        inputs[idx] = add(inputs[idx], Math.max(0, input));
        outputs[idx] = add(outputs[idx], Math.max(0, output));
    }

    // Exclude the current partial tick so block entity tick order does not change the reading
    public Rates rates(){
        long now = clock.getAsLong();
        double input = 0, output = 0;
        for(int idx = 0; idx < ticks.length; idx++){
            long age = now - ticks[idx];
            if(age <= 0 || age > WINDOW_TICKS) continue;
            input += inputs[idx];
            output += outputs[idx];
        }
        return new Rates(input / WINDOW_TICKS, output / WINDOW_TICKS);
    }

    private static long add(long first, long second){ return first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second; }

    public record Rates(double input, double output){
        public static final Rates ZERO = new Rates(0, 0);
        public Rates plus(Rates other){ return new Rates(input + other.input, output + other.output); }
    }
}
