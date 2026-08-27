package com.rieno.gadgetsandgizmos.lib.namedevents;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

// Carry one immutable named event through the shared transport bus
public record NamedEvent(
        UUID id,
        NamedEventSource source,
        String name,
        GraphValue data,
        int maximumDistance,
        Set<String> excludedTransports
) {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the named event
    public NamedEvent {
        id = id == null ? UUID.randomUUID() : id;
        if (source == null) {
            throw new IllegalArgumentException("Named Events require a source");
        }
        name = name == null ? "" : name.strip();
        if (name.isEmpty() || name.length() > 128) {
            throw new IllegalArgumentException("Named Event names must contain 1 through 128 characters");
        }
        data = data == null ? GraphValue.number(0.0D) : data;
        maximumDistance = Math.max(0, maximumDistance);
        LinkedHashSet<String> exclusions = new LinkedHashSet<>();
        if (excludedTransports != null) {
            excludedTransports.forEach(transport -> {
                if (transport != null && !transport.isBlank()) {
                    exclusions.add(transport.strip());
                }
            });
        }
        excludedTransports = Set.copyOf(exclusions);
    }

    // Create an unrestricted named event
    public static NamedEvent of(NamedEventSource source, String name, GraphValue data,
                                int maximumDistance) {
        return new NamedEvent(UUID.randomUUID(), source, name, data,
                maximumDistance, Set.of());
    }

    // Exclude one transport from this named event
    public NamedEvent excluding(String transportId) {
        LinkedHashSet<String> exclusions = new LinkedHashSet<>(excludedTransports);
        if (transportId != null && !transportId.isBlank()) {
            exclusions.add(transportId.strip());
        }
        return new NamedEvent(id, source, name, data, maximumDistance, exclusions);
    }
}
