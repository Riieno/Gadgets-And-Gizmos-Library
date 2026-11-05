package com.rieno.gadgetsandgizmos.lib.control;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

// Recognize repeated endpoint reversals across independently written control fields
public final class AlternatingValueSequence{
    private final Set<String> fields;
    private final double low;
    private final double high;
    private final long period;
    private final int reversals;
    private final Map<String, Sample> samples = new HashMap<>();

    public AlternatingValueSequence(Set<String> fields, double low, double high, long period, int reversals){
        if(fields.isEmpty() || !Double.isFinite(low) || !Double.isFinite(high)
                || low >= high || period <= 0 || reversals <= 0) throw new IllegalArgumentException("Invalid alternating sequence");
        this.fields = Set.copyOf(fields);
        this.low = low;
        this.high = high;
        this.period = period;
        this.reversals = reversals;
    }

    // Repeated assignments retain their original transition tick and cannot manufacture reversals
    public boolean observe(long tick, Map<String, Double> values){
        for(String field : fields){
            Double val = values.get(field);
            if(val == null) continue;
            if(!Double.isFinite(val) || val != low && val != high){ samples.remove(field); continue; }
            Sample prev = samples.get(field);
            long elapsed = prev == null ? -1 : tick - prev.tick();
            if(prev != null && elapsed >= 0 && elapsed <= period && val == prev.val()) continue;
            int count = prev != null && elapsed == period && val != prev.val()
                    ? Math.min(reversals, prev.reversals() + 1) : 0;
            samples.put(field, new Sample(val, tick, count));
        }
        return complete(tick);
    }

    // Every field must still belong to the same uninterrupted repeating sequence
    public boolean complete(long tick){
        for(String field : fields){
            Sample sample = samples.get(field);
            if(sample == null || sample.reversals() < reversals || tick < sample.tick()
                    || tick - sample.tick() > period) return false;
        }
        return true;
    }

    public void clear(){ samples.clear(); }

    private record Sample(double val, long tick, int reversals){}
}
