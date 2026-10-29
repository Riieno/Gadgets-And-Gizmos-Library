package com.rieno.gadgetsandgizmos.lib.display;

// Combine multiple presentations without letting a static view cancel a live one
public final class DisplayFrameDemand{
    private long requested = Long.MIN_VALUE;
    private boolean live;
    public void request(long frame, boolean update){
        if(requested != frame){ requested = frame; live = false; }
        live |= update;
    }
    public boolean live(long frame){ return requested != Long.MIN_VALUE && frame >= requested && frame - requested <= 1 && live; }
}
