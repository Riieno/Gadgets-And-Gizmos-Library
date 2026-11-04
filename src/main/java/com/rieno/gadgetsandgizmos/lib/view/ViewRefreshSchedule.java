package com.rieno.gadgetsandgizmos.lib.view;

// Schedule each feed independently without reducing its rate under render load
public final class ViewRefreshSchedule{
    private long next;
    private int rate;

    // Order due feeds by their existing deadline before spending the current frame's budget
    public long deadline(){ return next; }

    // Apply rate changes immediately and retain a fixed cadence between frames
    public boolean ready(long now, int refreshRate){
        int val = Math.clamp(refreshRate, 1, 60);
        if(rate != val){ rate = val; next = now; }
        return now >= next;
    }
    // Skip missed deadlines without accumulating a burst of old captures
    public void captured(long now){
        long interval = 1_000_000_000L / Math.max(1, rate);
        next += (Math.max(0, now - next) / interval + 1) * interval;
    }
    // Keep each source's cadence while spreading secondary renders across the refresh interval
    public void captured(long now, long phase){
        long interval = 1_000_000_000L / Math.max(1, rate);
        next = now + interval - Math.floorMod(now - phase, interval);
    }
}
