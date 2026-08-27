package com.rieno.gadgetsandgizmos.lib.namedevents;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Deliver named events between registered optional transports without implementation dependencies
public final class NamedEventBus {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAXIMUM_RECENT_EVENTS = 4096;
    private static final Map<String, NamedEventTransport> TRANSPORTS = new LinkedHashMap<>();
    private static final ArrayDeque<UUID> RECENT_EVENTS = new ArrayDeque<>();
    private static final Map<UUID, Boolean> RECENT_EVENT_IDS = new LinkedHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the named event bus
    private NamedEventBus() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Subscribe one transport to every compatible named event
    public static Subscription subscribe(String transportId, NamedEventTransport transport) {
        String id = normalizeTransportId(transportId);
        if (transport == null) {
            throw new IllegalArgumentException("Named Event transports cannot be null");
        }
        synchronized (NamedEventBus.class) {
            NamedEventTransport existing = TRANSPORTS.get(id);
            if (existing != null && existing != transport) {
                throw new IllegalStateException("Named Event transport '" + id + "' is already registered");
            }
            TRANSPORTS.put(id, transport);
        }
        return () -> unsubscribe(id, transport);
    }

    // Publish one named event to every compatible transport
    public static void publish(NamedEvent event) {
        if (event == null || !remember(event.id())) {
            return;
        }
        List<Map.Entry<String, NamedEventTransport>> transports;
        synchronized (NamedEventBus.class) {
            transports = new ArrayList<>(TRANSPORTS.entrySet());
        }
        for (Map.Entry<String, NamedEventTransport> entry : transports) {
            if (event.excludedTransports().contains(entry.getKey())) {
                continue;
            }
            try {
                entry.getValue().receive(event);
            } catch (RuntimeException err) {
                LOGGER.warn("[G&G][Named Events] Transport '{}' could not receive '{}': {}",
                        entry.getKey(), event.name(), err.getMessage(), err);
            }
        }
    }

    // Unsubscribe one exact transport registration
    private static void unsubscribe(String id, NamedEventTransport transport) {
        synchronized (NamedEventBus.class) {
            if (TRANSPORTS.get(id) == transport) {
                TRANSPORTS.remove(id);
            }
        }
    }

    // Remember one event id long enough to stop transport feedback loops
    private static synchronized boolean remember(UUID id) {
        if (RECENT_EVENT_IDS.containsKey(id)) {
            return false;
        }
        RECENT_EVENTS.addLast(id);
        RECENT_EVENT_IDS.put(id, Boolean.TRUE);
        while (RECENT_EVENTS.size() > MAXIMUM_RECENT_EVENTS) {
            RECENT_EVENT_IDS.remove(RECENT_EVENTS.removeFirst());
        }
        return true;
    }

    // Normalize a stable transport identifier
    private static String normalizeTransportId(String id) {
        String normalized = id == null ? "" : id.strip();
        if (normalized.isEmpty() || normalized.length() > 128) {
            throw new IllegalArgumentException("Named Event transport ids must contain 1 through 128 characters");
        }
        return normalized;
    }

    // Close one dynamic named event subscription
    @FunctionalInterface
    public interface Subscription extends AutoCloseable {
        // Unsubscribe this transport when its owning runtime is removed
        @Override
        void close();
    }
}
