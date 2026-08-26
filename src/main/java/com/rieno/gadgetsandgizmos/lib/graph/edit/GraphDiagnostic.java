package com.rieno.gadgetsandgizmos.lib.graph.edit;

// Describe one graph edit, validation, or export problem without host-specific types
public record GraphDiagnostic(String severity, String code, String message,
                              String nodeId, String edgeId) {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the graph diagnostic
    public GraphDiagnostic {
        severity = normalize(severity);
        code = normalize(code);
        message = message == null ? "" : message;
        nodeId = normalize(nodeId);
        edgeId = normalize(edgeId);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Normalize the diagnostic value
    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}
