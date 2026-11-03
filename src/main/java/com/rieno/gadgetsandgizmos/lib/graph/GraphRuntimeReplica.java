package com.rieno.gadgetsandgizmos.lib.graph;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

// Apply ordered runtime values while delivering every pulse separately on the owning thread
public final class GraphRuntimeReplica<V>{
    private final Map<GraphRuntimeJournal.Channel, Map<String, V>> values = new EnumMap<>(GraphRuntimeJournal.Channel.class);
    private UUID streamId;
    private long sequence;

    public GraphRuntimeReplica(){
        for(var channel : GraphRuntimeJournal.Channel.values()) values.put(channel, new LinkedHashMap<>());
    }

    public boolean accept(long base, GraphRuntimeJournal.Update<V> update, Consumer<GraphRuntimeJournal.Event<V>> pulses){
        if(base < 0 || update.sequence() <= base) return false;
        if(update.reset()){
            if(update.streamId().equals(streamId) && update.sequence() <= sequence) return false;
            values.values().forEach(Map::clear);
        }else if(!update.streamId().equals(streamId) || base != sequence) return false;
        streamId = update.streamId();
        sequence = update.sequence();
        for(var evt : update.events()){
            if(evt.channel() == GraphRuntimeJournal.Channel.PULSE){ pulses.accept(evt); continue; }
            Map<String, V> entries = values.get(evt.channel());
            if(evt.value() == null) entries.remove(evt.key());
            else entries.put(evt.key(), evt.value());
        }
        return true;
    }

    public Map<String, V> values(GraphRuntimeJournal.Channel channel){ return java.util.Collections.unmodifiableMap(values.get(channel)); }
    public UUID streamId(){ return streamId; }
    public long sequence(){ return sequence; }
    public void clear(){
        streamId = null;
        sequence = 0;
        values.values().forEach(Map::clear);
    }
}
