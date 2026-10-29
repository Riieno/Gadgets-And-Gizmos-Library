package com.rieno.gadgetsandgizmos.lib.util;

// Retain an idle resource for elapsed time rather than a varying number of rendered frames
public final class ResourceLease{
    private final long duration;
    private long renewed;
    private boolean active;

    public ResourceLease(long idleNanos){
        if(idleNanos < 0) throw new IllegalArgumentException("Idle duration must not be negative");
        duration = idleNanos;
    }

    // Use one monotonic clock for renewal and expiry checks
    public void renew(long now){ renewed = now; active = true; }
    public boolean isExpired(long now){ return !active || now - renewed >= duration; }
}
