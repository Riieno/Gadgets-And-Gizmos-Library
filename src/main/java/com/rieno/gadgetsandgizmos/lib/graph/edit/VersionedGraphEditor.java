package com.rieno.gadgetsandgizmos.lib.graph.edit;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.graph.GraphNodeDefinition;

import java.util.List;

// Edit a copied draft and publish it only when its expected revision still matches
public interface VersionedGraphEditor {
    // Available graph snapshots
    enum View {
        DRAFT,
        ACTIVE
    }

    // Read one graph snapshot
    GraphDocumentSnapshot snapshot(View view);

    // Read every registered node type
    List<GraphNodeDefinition> nodeTypes();

    // Mutate the draft atomically
    GraphEditResult mutateDraft(int expectedRevision, List<GraphMutation> mutations);

    // Validate the current draft
    GraphEditResult validateDraft();

    // Apply the current draft
    GraphEditResult applyDraft(int expectedRevision);
}
