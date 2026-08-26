package com.rieno.gadgetsandgizmos.lib.graph.edit;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;

import java.util.Map;

// Describe one bounded mutation against a versioned draft graph
public sealed interface GraphMutation permits GraphMutation.AddNode,
        GraphMutation.RemoveNode, GraphMutation.MoveNode, GraphMutation.RenameNode,
        GraphMutation.SetNodeAlias,
        GraphMutation.SetNodeData, GraphMutation.RemoveNodeData,
        GraphMutation.AddEdge, GraphMutation.RemoveEdge,
        GraphMutation.AddFunction, GraphMutation.RenameFunction, GraphMutation.RemoveFunction,
        GraphMutation.SetVariable, GraphMutation.RemoveVariable {
    // A blank function id targets the root graph
    record AddNode(String functionId, String temporaryId, String type, String label, String alias,
                   double x, double y,
                   GraphDataValue.CompoundValue data) implements GraphMutation {
        public AddNode {
            alias = GraphNodeAlias.normalize(alias);
            data = data == null
                    ? new GraphDataValue.CompoundValue(Map.of()) : data;
        }
    }

    record RemoveNode(String functionId, String nodeId) implements GraphMutation {
    }

    record MoveNode(String functionId, String nodeId, double x, double y) implements GraphMutation {
    }

    record RenameNode(String functionId, String nodeId, String label) implements GraphMutation {
    }

    record SetNodeAlias(String functionId, String nodeId, String alias) implements GraphMutation {
        public SetNodeAlias {
            alias = GraphNodeAlias.normalize(alias);
        }
    }

    record SetNodeData(String functionId, String nodeId,
                       String key, GraphDataValue value) implements GraphMutation {
    }

    record RemoveNodeData(String functionId, String nodeId, String key) implements GraphMutation {
    }

    record AddEdge(String functionId, String temporaryId, String fromNode, String fromPort,
                   String toNode, String toPort) implements GraphMutation {
    }

    record RemoveEdge(String functionId, String edgeId) implements GraphMutation {
    }

    record AddFunction(String temporaryId, String name) implements GraphMutation {
    }

    record RenameFunction(String functionId, String name) implements GraphMutation {
    }

    record RemoveFunction(String functionId) implements GraphMutation {
    }

    record SetVariable(String name, GraphValue value) implements GraphMutation {
    }

    record RemoveVariable(String name) implements GraphMutation {
    }
}
