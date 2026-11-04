package com.rieno.gadgetsandgizmos.lib.time;

import java.time.Clock;
import java.util.Objects;
import java.util.function.LongSupplier;

// Supply the owning world's day clock and an injectable wall clock
public record TimeSource(LongSupplier dayTime, Clock clock){
    // Require live suppliers while allowing fixed clocks in deterministic consumers
    public TimeSource{ Objects.requireNonNull(dayTime); Objects.requireNonNull(clock); }
}
