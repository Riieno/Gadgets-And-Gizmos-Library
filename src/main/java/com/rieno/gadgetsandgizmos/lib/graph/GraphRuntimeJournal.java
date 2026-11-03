package com.rieno.gadgetsandgizmos.lib.graph;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

// Publish ordered value changes and every execution pulse on the owning thread
public final class GraphRuntimeJournal<V>{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        DEFAULTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    public enum Channel{ INPUT, OUTPUT, VARIABLE, PULSE, STATUS }

    private final int capacity;
    private final Map<Channel, Map<String, V>> values = new EnumMap<>(Channel.class);
    private final ArrayDeque<Event<V>> events = new ArrayDeque<>();
    private UUID streamId = UUID.randomUUID();
    private long sequence;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                       FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    public GraphRuntimeJournal(int capacity){
        if(capacity < 1) throw new IllegalArgumentException("Runtime history must be positive");
        this.capacity = capacity;
        for(Channel channel : Channel.values()) values.put(channel, new LinkedHashMap<>());
    }

    // Record changed values without retaining another complete map for each observer
    public void value(Channel channel, String key, V val, long tick, int revision, UUID playerId){
        if(channel == Channel.PULSE) throw new IllegalArgumentException("Use pulse for execution events");
        Objects.requireNonNull(key, "key");
        V prev = val == null ? values.get(channel).remove(key) : values.get(channel).put(key, val);
        if(Objects.equals(prev, val)) return;
        append(new Event<>(++sequence, tick, revision, playerId, channel, key, val));
    }

    // Equal values and repeated pulses in the same tick remain distinct executions
    public void pulse(String key, long tick, int revision, UUID playerId){
        append(new Event<>(++sequence, tick, revision, playerId, Channel.PULSE,
                Objects.requireNonNull(key, "key"), null));
    }

    // Initialise a reader or explicitly report that its retained history has expired
    public Update<V> read(UUID readerStream, long after){
        boolean reset = !streamId.equals(readerStream) || after < 0 || after > sequence
                || !events.isEmpty() && after < events.getFirst().sequence() - 1;
        List<Event<V>> res = new ArrayList<>();
        if(reset){
            values.forEach((channel, entries) -> entries.forEach((key, val) ->
                    res.add(new Event<>(sequence, 0, 0, null, channel, key, val))));
        }else{
            var iterator = events.descendingIterator();
            while(iterator.hasNext()){
                Event<V> evt = iterator.next();
                if(evt.sequence() <= after) break;
                res.add(evt);
            }
            java.util.Collections.reverse(res);
        }
        return new Update<>(streamId, sequence, reset, res);
    }

    // Start a distinct lifecycle so old observers cannot merge unrelated executions
    public void clear(){
        streamId = UUID.randomUUID();
        sequence = 0;
        events.clear();
        values.values().forEach(Map::clear);
    }

    private void append(Event<V> evt){
        events.addLast(evt);
        while(events.size() > capacity) events.removeFirst();
    }

    public record Event<V>(long sequence, long tick, int revision, UUID playerId,
                            Channel channel, String key, V value){}

    // A reset restores current values; expired execution events are never fabricated
    public record Update<V>(UUID streamId, long sequence, boolean reset, List<Event<V>> events){
        public Update{ events = List.copyOf(events); }
    }
}
