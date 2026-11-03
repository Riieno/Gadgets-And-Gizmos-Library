package com.rieno.gadgetsandgizmos.lib.nbt;

import com.rieno.gadgetsandgizmos.lib.util.ValueUpdateSequence;
import net.minecraft.nbt.CompoundTag;

// Merge an ordered document publication without accepting missing or replayed changes
public final class CompoundTagReplica{
    private final ValueUpdateSequence sequence = new ValueUpdateSequence();
    private CompoundTag document = new CompoundTag();

    public boolean accept(CompoundTagPublication.Update update){
        if(!sequence.accept(update.streamId(), update.base(), update.sequence(), update.full())) return false;
        if(update.full()) document = update.patch().copy();
        else CompoundTagDelta.apply(document, update.patch());
        return true;
    }

    // The owning reader may inspect this live document but must not mutate it
    public CompoundTag document(){ return document; }
    public void clear(){ sequence.reset(); document = new CompoundTag(); }
}
