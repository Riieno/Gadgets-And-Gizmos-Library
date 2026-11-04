package com.rieno.gadgetsandgizmos.lib.view;

// Spread due captures across player frames while retaining enough capacity for the requested rate
public final class ViewRefreshBudget{
    private final long[] samples = new long[32];
    private long prev;
    private long total;
    private int idx;
    private int count;

    // Measure the complete player frame, including secondary views, before choosing this frame's capacity
    public int allowance(long now, int sources, int refreshRate){
        if(prev != 0){
            long elapsed = Math.max(1, now - prev);
            if(elapsed <= 1_000_000_000L){
                total -= samples[idx];
                total += elapsed;
                samples[idx] = elapsed;
                idx = (idx + 1) % samples.length;
                count = Math.min(samples.length, count + 1);
            }
        }
        prev = now;
        if(sources <= 0) return 0;
        double frame = count == 0 ? 1_000_000_000D / 60 : total / (double)count;
        int needed = (int)Math.ceil(sources * (double)Math.clamp(refreshRate, 1, 60) * frame / 1_000_000_000D);
        return Math.clamp(needed, 1, sources);
    }

    // Discard timing from the previous world or renderer session
    public void reset(){
        java.util.Arrays.fill(samples, 0);
        prev = total = 0;
        idx = count = 0;
    }
}
