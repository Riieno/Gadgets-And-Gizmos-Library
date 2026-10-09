package com.rieno.gadgetsandgizmos.lib.view;

// Schedule each feed independently without reducing its rate under render load
public final class ViewRefreshSchedule{
    private long next;
    private int rate;

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
}
