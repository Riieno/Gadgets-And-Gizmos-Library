package com.rieno.gadgetsandgizmos.lib.graph.export;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

// Keep target and node emitters ordered and reject conflicting registrations
public final class GraphCodeEmitterRegistry<C> {
    // Target and node type form the complete registration identity
    public record Key(String target, String nodeType) {
        public Key {
            target = normalize(target, "target");
            nodeType = normalize(nodeType, "nodeType");
        }
    }

    // Tracked emitters
    private final Map<Key, GraphCodeEmitter<C>> emitters = new LinkedHashMap<>();

    // Register one emitter without replacing an existing implementation
    public synchronized GraphCodeEmitter<C> register(
            String target,
            String nodeType,
            GraphCodeEmitter<C> emitter
    ) {
        Key key = new Key(target, nodeType);
        GraphCodeEmitter<C> normalized = Objects.requireNonNull(emitter, "emitter");
        GraphCodeEmitter<C> previous = emitters.putIfAbsent(key, normalized);
        if (previous != null && previous != normalized) {
            throw new IllegalStateException(
                    "Graph code emitter is already registered: " + target + "/" + nodeType);
        }
        return previous == null ? normalized : previous;
    }

    // Find one emitter without exposing the live registry map
    public synchronized Optional<GraphCodeEmitter<C>> find(String target, String nodeType) {
        return Optional.ofNullable(emitters.get(new Key(target, nodeType)));
    }

    // Return a stable registration snapshot
    public synchronized Map<Key, GraphCodeEmitter<C>> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(emitters));
    }

    // Normalize one registry identifier
    private static String normalize(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
