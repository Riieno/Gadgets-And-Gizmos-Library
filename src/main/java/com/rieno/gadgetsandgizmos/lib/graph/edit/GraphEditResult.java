package com.rieno.gadgetsandgizmos.lib.graph.edit;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.List;
import java.util.Map;

// Report a graph transaction without throwing away validator detail
public record GraphEditResult(boolean saved, boolean applied, boolean valid,
                              int revision, String code, String message,
                              Map<String, String> resolvedIds,
                              List<GraphDiagnostic> diagnostics) {
    // Initialize the graph edit result
    public GraphEditResult {
        revision = Math.max(-1, revision);
        code = code == null ? "" : code.strip();
        message = message == null ? "" : message;
        resolvedIds = Map.copyOf(resolvedIds == null ? Map.of() : resolvedIds);
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
    }
}
