package com.rieno.gadgetsandgizmos.lib.namedevents;

// Receive named events from the shared Gadgets & Gizmos transport bus
@FunctionalInterface
public interface NamedEventTransport {
    // Receive one immutable named event
    void receive(NamedEvent event);
}
