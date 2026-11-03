package com.rieno.gadgetsandgizmos.lib.nbt;

import net.minecraft.nbt.CompoundTag;
import java.util.UUID;

// Publish changed document fields while retaining one mutable baseline on the owning thread
public final class CompoundTagPublication{
    private CompoundTag document;
    private UUID streamId = UUID.randomUUID();
    private long sequence;

    public Update publish(CompoundTag next){
        return publish(next, false);
    }

    // Explicit operations may acknowledge unchanged state with an empty delta
    public Update publish(CompoundTag next, boolean acknowledge){
        boolean full = document == null;
        if(!full && !acknowledge && document.equals(next)) return null;
        CompoundTag patch = full ? next.copy() : CompoundTagDelta.between(document, next);
        long base = sequence++;
        if(full) document = next.copy();
        else CompoundTagDelta.apply(document, patch);
        return new Update(streamId, base, sequence, full, patch);
    }

    public void reset(){
        document = null;
        sequence = 0;
        streamId = UUID.randomUUID();
    }

    public record Update(UUID streamId, long base, long sequence, boolean full, CompoundTag patch){
        public Update{ patch = patch.copy(); }

        public CompoundTag toTag(){
            CompoundTag res = new CompoundTag();
            res.putUUID("Stream", streamId);
            res.putLong("Base", base);
            res.putLong("Sequence", sequence);
            res.putBoolean("Full", full);
            res.put("Patch", patch.copy());
            return res;
        }

        public static Update fromTag(CompoundTag data){
            return new Update(data.getUUID("Stream"), data.getLong("Base"), data.getLong("Sequence"),
                    data.getBoolean("Full"), data.getCompound("Patch"));
        }
    }
}
