package com.rieno.gadgetsandgizmos.lib.scratch;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.Map;

// Describe one host-provided Scratch-style block without coupling the library to a graph implementation.
public record ScratchBlockDefinition(
        String id,
        String category,
        String title,
        int colour,
        Map<String, String> fields
) {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the Scratch block definition
    public ScratchBlockDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("A Scratch block definition requires an id");
        }
        category = category == null || category.isBlank() ? "core" : category.trim();
        title = title == null || title.isBlank() ? id : title.trim();
        fields = Map.copyOf(fields == null ? Map.of() : fields);
    }
}
