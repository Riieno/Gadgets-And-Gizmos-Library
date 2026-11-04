package com.rieno.gadgetsandgizmos.lib.util;

import java.util.Objects;
import java.util.UUID;

// Reject deltas that do not follow the reader's current complete baseline
public final class ValueUpdateSequence{
    // Track one ordered update stream on the owning thread
    private UUID streamId;
    private long sequence;

    // Advance only after a valid full baseline or the next matching delta
    public boolean accept(UUID streamId, long base, long next, boolean full){
        Objects.requireNonNull(streamId, "streamId");
        if(base < 0 || next <= base || next != base + 1) return false;
        if(full){
            if(streamId.equals(this.streamId) && next <= sequence) return false;
        }else if(!streamId.equals(this.streamId) || base != sequence) return false;
        this.streamId = streamId;
        sequence = next;
        return true;
    }

    // Require a new full baseline after a graph or client lifecycle change
    public void reset(){
        streamId = null;
        sequence = 0;
    }
}
