package com.rieno.gadgetsandgizmos.lib.client.render;

import it.unimi.dsi.fastutil.longs.Long2BooleanLinkedOpenHashMap;
import net.minecraft.core.SectionPos;

// Merge repeated section notifications until their owning renderer next consumes them
public final class SectionUpdateQueue{
    private Long2BooleanLinkedOpenHashMap pending = new Long2BooleanLinkedOpenHashMap();
    private Long2BooleanLinkedOpenHashMap draining = new Long2BooleanLinkedOpenHashMap();

    // Retain player priority when either notification requests an immediate rebuild
    public void add(int x, int y, int z, boolean immediate){
        long key = SectionPos.asLong(x, y, z);
        pending.put(key, immediate || pending.get(key));
    }

    // Deliver each section once while retaining notifications produced by the consumer
    public void drain(Consumer consumer){
        if(pending.isEmpty()) return;
        var prev = draining;
        draining = pending;
        pending = prev;
        try{
            var entries = draining.long2BooleanEntrySet().fastIterator();
            while(entries.hasNext()){
                var entry = entries.next();
                long key = entry.getLongKey();
                consumer.accept(SectionPos.x(key), SectionPos.y(key), SectionPos.z(key), entry.getBooleanValue());
                entries.remove();
            }
        }finally{
            draining.long2BooleanEntrySet().forEach(entry -> {
                long key = entry.getLongKey();
                pending.put(key, entry.getBooleanValue() || pending.get(key));
            });
            draining.clear();
        }
    }

    // Discard notifications when all of the owner's meshes are replaced
    public void clear(){ pending.clear(); }

    @FunctionalInterface
    public interface Consumer{
        void accept(int x, int y, int z, boolean immediate);
    }
}
