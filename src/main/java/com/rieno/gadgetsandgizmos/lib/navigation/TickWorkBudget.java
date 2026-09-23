package com.rieno.gadgetsandgizmos.lib.navigation;

import java.util.function.LongSupplier;

// Share measured game-thread work without spending the tick allowance while idle
public final class TickWorkBudget{
    private final long maximumNanos;
    private final long maximumSliceNanos;
    private final LongSupplier clock;
    private long tick = Long.MIN_VALUE;
    private long remainingNanos;

    // Configure the total and individual operation allowances
    public TickWorkBudget(long maximumNanos, long maximumSliceNanos){
        this(maximumNanos, maximumSliceNanos, System::nanoTime);
    }

    // Supply a monotonic clock for deterministic callers
    public TickWorkBudget(long maximumNanos, long maximumSliceNanos, LongSupplier clock){
        this.maximumNanos = Math.max(1L, maximumNanos);
        this.maximumSliceNanos = Math.max(1L, maximumSliceNanos);
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    // Claim one bounded operation on the owning game thread
    public Slice claim(long tick){
        if(this.tick != tick){
            this.tick = tick;
            remainingNanos = maximumNanos;
        }
        return new Slice(clock.getAsLong(), Math.min(remainingNanos, maximumSliceNanos));
    }

    // Charge only time actually spent inside this operation
    public final class Slice implements AutoCloseable{
        private final long startedNanos;
        private final long allowedNanos;
        private boolean closed;

        private Slice(long startedNanos, long allowedNanos){
            this.startedNanos = startedNanos;
            this.allowedNanos = allowedNanos;
        }

        // Check before another expensive validation
        public boolean available(){
            return !closed && remainingNanos() > 0L;
        }

        // Get the maximum time allowed for the next incremental operation
        public long remainingNanos(){
            return Math.max(0L, allowedNanos - Math.max(0L, clock.getAsLong() - startedNanos));
        }

        @Override
        public void close(){
            if(closed) return;
            closed = true;
            TickWorkBudget.this.remainingNanos = Math.max(0L,
                    TickWorkBudget.this.remainingNanos - Math.max(0L, clock.getAsLong() - startedNanos));
        }
    }
}
