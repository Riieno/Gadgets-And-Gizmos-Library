package com.rieno.gadgetsandgizmos.lib.graph;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.Collection;

// Define the minimum node and wire data needed by the reusable graph compiler
public interface GraphModel<N extends GraphModel.Node, E extends GraphModel.Edge> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the nodes
    Collection<N> nodes();

    // Get the edges
    Collection<E> edges();

    // Expose the node
    interface Node {
        // Get the id
        String id();

        // Get the type
        String type();
    }

    // Expose the edge
    interface Edge {
        // Get the id
        String id();

        // Create the edge from node
        String fromNode();

        // Create the edge from port
        String fromPort();

        // Convert the edge to node
        String toNode();

        // Convert the edge to port
        String toPort();
    }
}
