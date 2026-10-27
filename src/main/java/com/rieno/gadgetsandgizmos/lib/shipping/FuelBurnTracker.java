package com.rieno.gadgetsandgizmos.lib.shipping;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

// Measure depletion per resource so FE, item counts and fluid volumes are never added together
public final class FuelBurnTracker<K>{
    private record Sample<K>(long ticks, Map<K, Double> used){}
    private final ArrayDeque<Sample<K>> samples = new ArrayDeque<>();
    private final long window;
    private final Map<K, Double> consumed = new HashMap<>();
    private Map<K, Double> previous = Map.of();
    private long previousTick = Long.MIN_VALUE;

    public FuelBurnTracker(long windowTicks){ window = Math.max(20L, windowTicks); }

    // Compare fuel before transfers against the previous post-transfer baseline
    public void sample(long tick, Map<K, Double> amounts){
        if(previousTick != Long.MIN_VALUE && tick > previousTick && tick - previousTick <= window){
            Map<K, Double> used = new HashMap<>(consumed);
            consumed.clear();
            previous.forEach((key, amount) -> {
                double delta = amount - amounts.getOrDefault(key, 0.0D);
                if(delta > 0.0D && Double.isFinite(delta)) used.merge(key, delta, Double::sum);
            });
            samples.addLast(new Sample<>(tick - previousTick, Map.copyOf(used)));
            long ticks = samples.stream().mapToLong(Sample::ticks).sum();
            while(samples.size() > 1 && ticks - samples.getFirst().ticks() >= window){
                ticks -= samples.removeFirst().ticks();
            }
        }else if(previousTick != Long.MIN_VALUE && tick != previousTick){
            samples.clear();
            consumed.clear();
        }
        baseline(tick, amounts);
    }

    // Exclude refuelling, docking transfers and topology changes from measured consumption
    public void baseline(long tick, Map<K, Double> amounts){
        previousTick = tick;
        previous = Map.copyOf(amounts);
    }

    public void reset(){ samples.clear(); consumed.clear(); previous = Map.of(); previousTick = Long.MIN_VALUE; }

    // Flow-through consumers can use fuel immediately without retaining it in a readable buffer
    public void recordConsumption(Map<K, Double> amounts){
        amounts.forEach((key, amount) -> {
            if(amount > 0.0D && Double.isFinite(amount)) consumed.merge(key, amount, Double::sum);
        });
    }

    public Map<K, Double> rates(){
        Map<K, Double> rates = new HashMap<>();
        long ticks = samples.stream().mapToLong(Sample::ticks).sum();
        if(ticks == 0L) return Map.of();
        for(Sample<K> sample : samples) sample.used().forEach((key, val) -> rates.merge(key, val / ticks, Double::sum));
        return Map.copyOf(rates);
    }

    // The first exhausted fuel supply limits the range of a vehicle using several fuels
    public double remainingTicks(Map<K, Double> amounts, Map<K, Double> fallbackRates){
        return remainingTicks(amounts, fallbackRates, null);
    }

    // Retired fuel types no longer limit a consumer which has switched to another supply
    public double remainingTicks(Map<K, Double> amounts, Map<K, Double> fallbackRates, Set<K> required){
        Map<K, Double> rates = new HashMap<>(fallbackRates);
        rates().forEach((key, val) -> { if(val > 0.0D) rates.put(key, val); });
        double remaining = Double.POSITIVE_INFINITY;
        for(var entry : rates.entrySet()){
            if(required != null && !required.contains(entry.getKey())) continue;
            if(entry.getValue() > 0.0D) remaining = Math.min(remaining,
                    Math.max(0.0D, amounts.getOrDefault(entry.getKey(), 0.0D)) / entry.getValue());
        }
        return remaining;
    }
}
