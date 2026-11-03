package com.rieno.gadgetsandgizmos.lib.graph;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.function.Function;

// Encode runtime changes without exposing the consuming mod's value implementation
public final class GraphRuntimeNbt{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        DEFAULTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                       FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private GraphRuntimeNbt(){}

    public static <V> CompoundTag write(GraphRuntimeJournal.Update<V> update, Function<V, CompoundTag> encode){
        CompoundTag root = new CompoundTag();
        root.putUUID("Stream", update.streamId());
        root.putLong("Sequence", update.sequence());
        root.putBoolean("Reset", update.reset());
        ListTag entries = new ListTag();
        for(var evt : update.events()){
            CompoundTag entry = new CompoundTag();
            entry.putLong("Sequence", evt.sequence());
            entry.putLong("Tick", evt.tick());
            entry.putInt("Revision", evt.revision());
            if(evt.playerId() != null) entry.putUUID("Player", evt.playerId());
            entry.putString("Channel", evt.channel().name());
            entry.putString("Key", evt.key());
            if(evt.value() != null) entry.put("Value", encode.apply(evt.value()));
            entries.add(entry);
        }
        root.put("Events", entries);
        return root;
    }

    public static <V> GraphRuntimeJournal.Update<V> read(CompoundTag root, Function<CompoundTag, V> decode){
        var entries = root.getList("Events", Tag.TAG_COMPOUND);
        if(entries.size() > 65536) throw new IllegalArgumentException("Too many runtime changes");
        var events = new ArrayList<GraphRuntimeJournal.Event<V>>(entries.size());
        for(int idx = 0; idx < entries.size(); idx++){
            CompoundTag entry = entries.getCompound(idx);
            events.add(new GraphRuntimeJournal.Event<>(entry.getLong("Sequence"), entry.getLong("Tick"),
                    entry.getInt("Revision"), entry.hasUUID("Player") ? entry.getUUID("Player") : null,
                    GraphRuntimeJournal.Channel.valueOf(entry.getString("Channel")), entry.getString("Key"),
                    entry.contains("Value") ? decode.apply(entry.getCompound("Value")) : null));
        }
        return new GraphRuntimeJournal.Update<>(root.getUUID("Stream"), root.getLong("Sequence"),
                root.getBoolean("Reset"), events);
    }
}
