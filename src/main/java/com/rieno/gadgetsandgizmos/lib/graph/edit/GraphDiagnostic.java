package com.rieno.gadgetsandgizmos.lib.graph.edit;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.graph.GraphModel;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

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

    // Attribute wire failures to both endpoints and combine distinct reasons for each node
    public static Map<String, List<String>> errorsByNode(Collection<GraphDiagnostic> diagnostics,
                                                       Collection<? extends GraphModel.Edge> edges){
        Map<String, GraphModel.Edge> wires = new LinkedHashMap<>();
        for(var edge : edges) wires.putIfAbsent(edge.id(), edge);
        Map<String, LinkedHashSet<String>> messages = new LinkedHashMap<>();
        for(var diagnostic : diagnostics){
            if(diagnostic == null || !"error".equalsIgnoreCase(diagnostic.severity())) continue;
            String text = diagnostic.message().isBlank() ? diagnostic.code() : diagnostic.message();
            if(text.isBlank()) text = "Graph validation failed";
            addMessage(messages, diagnostic.nodeId(), text);
            var edge = wires.get(diagnostic.edgeId());
            if(edge != null){ addMessage(messages, edge.fromNode(), text); addMessage(messages, edge.toNode(), text); }
        }
        Map<String, List<String>> res = new LinkedHashMap<>();
        messages.forEach((id, values) -> res.put(id, List.copyOf(values)));
        return Collections.unmodifiableMap(res);
    }

    private static void addMessage(Map<String, LinkedHashSet<String>> messages, String id, String text){
        if(!id.isBlank()) messages.computeIfAbsent(id, ignored -> new LinkedHashSet<>()).add(text);
    }
}
