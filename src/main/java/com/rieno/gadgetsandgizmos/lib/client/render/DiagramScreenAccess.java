package com.rieno.gadgetsandgizmos.lib.client.render;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.sublevel.SubLevel;

// Expose the private hosted-diagram controls needed by embedded renderers
public interface DiagramScreenAccess {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the diagram viewport orientation
    void ct$updateViewportOrientation();

    // Rotate the hosted diagram
    void ct$rotateDiagram(int yawSteps, int pitchSteps);

    // Render the hosted diagram contents
    void ct$renderContents(SubLevel subLevel, float partialTick);

    // Release the hosted diagram framebuffers
    void ct$freeFramebuffers();

    // Check if the diagram paper is visible
    boolean ct$isPaperVisible();

    // Set the diagram paper visibility
    void ct$setPaperVisible(boolean visible);

    // Set the previous paper offset
    void ct$setLastPaperOffset(float offset);

    // Set the current paper offset
    void ct$setPaperOffset(float offset);

    // Set the previous tab offset
    void ct$setLastTabOffset(float offset);

    // Set the current tab offset
    void ct$setTabOffset(float offset);

    // Set the render timer
    void ct$setRenderTime(float renderTime);
}
