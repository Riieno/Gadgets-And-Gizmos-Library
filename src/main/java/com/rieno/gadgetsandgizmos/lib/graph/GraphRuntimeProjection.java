package com.rieno.gadgetsandgizmos.lib.graph;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;

// Project shared runtime changes with personal overrides without copying the shared value maps
public final class GraphRuntimeProjection<V>{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        DEFAULTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private final Map<GraphRuntimeJournal.Channel, Map<String, V>> overrides = new EnumMap<>(GraphRuntimeJournal.Channel.class);
    private UUID sourceStream;
    private long sourceSequence;
    private UUID streamId = UUID.randomUUID();
    private long sequence;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                       FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    public UUID sourceStream(){ return sourceStream; }
    public long sourceSequence(){ return sourceSequence; }
    public long sequence(){ return sequence; }

    // Source values are queried only when a personal override is removed
    public GraphRuntimeJournal.Update<V> project(GraphRuntimeJournal.Update<V> source,
            Map<GraphRuntimeJournal.Channel, Map<String, V>> next, BiFunction<GraphRuntimeJournal.Channel, String, V> lookup,
            boolean metadataChanged){
        boolean reset = source.reset() || !Objects.equals(sourceStream, source.streamId());
        if(reset){
            streamId = UUID.randomUUID();
            sequence = 0;
            overrides.clear();
        }
        var events = new ArrayList<GraphRuntimeJournal.Event<V>>();
        for(var evt : source.events()){
            if(evt.channel() != GraphRuntimeJournal.Channel.PULSE
                    && next.getOrDefault(evt.channel(), Map.of()).containsKey(evt.key())) continue;
            events.add(new GraphRuntimeJournal.Event<>(++sequence, evt.tick(), evt.revision(), evt.playerId(),
                    evt.channel(), evt.key(), evt.value()));
        }
        for(var channel : GraphRuntimeJournal.Channel.values()){
            if(channel == GraphRuntimeJournal.Channel.PULSE) continue;
            Map<String, V> prev = overrides.getOrDefault(channel, Map.of());
            Map<String, V> values = next.getOrDefault(channel, Map.of());
            for(var entry : values.entrySet()){
                if(!reset && Objects.equals(prev.get(entry.getKey()), entry.getValue())) continue;
                events.add(new GraphRuntimeJournal.Event<>(++sequence, 0, 0, null, channel, entry.getKey(), entry.getValue()));
            }
            for(String key : prev.keySet()){
                if(values.containsKey(key)) continue;
                events.add(new GraphRuntimeJournal.Event<>(++sequence, 0, 0, null, channel, key, lookup.apply(channel, key)));
            }
            if(!prev.equals(values)) overrides.put(channel, new LinkedHashMap<>(values));
        }
        if(events.isEmpty() && (metadataChanged || reset)) sequence++;
        sourceStream = source.streamId();
        sourceSequence = source.sequence();
        return new GraphRuntimeJournal.Update<>(streamId, sequence, reset, events);
    }
}
