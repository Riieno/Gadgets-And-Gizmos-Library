package com.rieno.gadgetsandgizmos.lib.scratch;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

// Keep a host's block palette ordered and reject conflicting registrations.
public final class ScratchBlockRegistry {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Registered blocks
    private final Map<String, ScratchBlockDefinition> definitions = new LinkedHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Register one host-owned block
    public synchronized ScratchBlockDefinition register(ScratchBlockDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        ScratchBlockDefinition existing = definitions.putIfAbsent(definition.id(), definition);
        if (existing != null && !existing.equals(definition)) {
            throw new IllegalStateException("Scratch block type is already registered: " + definition.id());
        }
        return existing == null ? definition : existing;
    }

    // Get one registered block
    public synchronized ScratchBlockDefinition get(String id) {
        return definitions.get(id);
    }

    // Get the ordered block palette
    public synchronized Collection<ScratchBlockDefinition> definitions() {
        return definitions.values().stream().toList();
    }

    // Get a stable snapshot for a client renderer
    public synchronized Map<String, ScratchBlockDefinition> snapshot() {
        return Map.copyOf(definitions);
    }
}
