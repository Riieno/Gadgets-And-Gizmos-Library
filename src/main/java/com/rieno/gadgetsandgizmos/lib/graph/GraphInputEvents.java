package com.rieno.gadgetsandgizmos.lib.graph;

import java.util.UUID;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

// Deliver external inputs to independent execution sessions on the owning thread
public final class GraphInputEvents<V>{
    private final CopyOnWriteArrayList<Consumer<Event<V>>> receivers = new CopyOnWriteArrayList<>();
    private final Consumer<RuntimeException> errors;

    public GraphInputEvents(Consumer<RuntimeException> errors){ this.errors = Objects.requireNonNull(errors); }

    public Subscription subscribe(Consumer<Event<V>> receiver){
        Objects.requireNonNull(receiver);
        Consumer<Event<V>> subscribed = evt -> receiver.accept(evt);
        receivers.add(subscribed);
        return new Subscription(){
            private boolean closed;
            @Override public void close(){
                if(closed) return;
                closed = true;
                receivers.remove(subscribed);
            }
        };
    }

    // Repeated inputs remain separate events; a failing observer cannot stop its source
    public void publish(String id, V value, UUID playerId){
        if(receivers.isEmpty()) return;
        Event<V> evt = new Event<>(id, value, playerId);
        for(var receiver : receivers){
            try{ receiver.accept(evt); }
            catch(RuntimeException err){ errors.accept(err); }
        }
    }

    public record Event<V>(String id, V value, UUID playerId){}

    public interface Subscription extends AutoCloseable{
        @Override void close();
    }
}
