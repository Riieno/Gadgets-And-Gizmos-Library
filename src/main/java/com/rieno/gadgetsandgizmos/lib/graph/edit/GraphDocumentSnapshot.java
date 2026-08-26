package com.rieno.gadgetsandgizmos.lib.graph.edit;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;

import java.util.List;
import java.util.Map;

// Publish one immutable logical graph document snapshot
public record GraphDocumentSnapshot(
        int version,
        int revision,
        String templateId,
        double viewportX,
        double viewportY,
        double viewportZoom,
        List<Node> nodes,
        List<Edge> edges,
        List<FunctionGraph> functions,
        Map<String, GraphValue> variables
) {
    // Initialize the graph document snapshot
    public GraphDocumentSnapshot {
        version = Math.max(0, version);
        revision = Math.max(0, revision);
        templateId = templateId == null ? "" : templateId;
        nodes = List.copyOf(nodes == null ? List.of() : nodes);
        edges = List.copyOf(edges == null ? List.of() : edges);
        functions = List.copyOf(functions == null ? List.of() : functions);
        variables = Map.copyOf(variables == null ? Map.of() : variables);
    }

    // Publish one immutable node
    public record Node(String id, String type, String label,
                       double x, double y,
                       Map<String, String> inputs,
                       Map<String, String> outputs,
                       GraphDataValue.CompoundValue data) {
        public Node {
            id = id == null ? "" : id;
            type = type == null ? "" : type;
            label = label == null ? "" : label;
            inputs = Map.copyOf(inputs == null ? Map.of() : inputs);
            outputs = Map.copyOf(outputs == null ? Map.of() : outputs);
            data = data == null
                    ? new GraphDataValue.CompoundValue(Map.of()) : data;
        }
    }

    // Publish one immutable edge
    public record Edge(String id, String fromNode, String fromPort,
                       String toNode, String toPort) {
        public Edge {
            id = normalize(id);
            fromNode = normalize(fromNode);
            fromPort = normalize(fromPort);
            toNode = normalize(toNode);
            toPort = normalize(toPort);
        }
    }

    // Publish one immutable function graph
    public record FunctionGraph(String id, String name,
                                double viewportX, double viewportY, double viewportZoom,
                                List<Node> nodes, List<Edge> edges) {
        public FunctionGraph {
            id = normalize(id);
            name = name == null ? "" : name;
            nodes = List.copyOf(nodes == null ? List.of() : nodes);
            edges = List.copyOf(edges == null ? List.of() : edges);
        }
    }

    // Normalize one graph identifier
    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}
