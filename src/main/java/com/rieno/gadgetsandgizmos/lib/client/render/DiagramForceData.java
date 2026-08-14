package com.rieno.gadgetsandgizmos.lib.client.render;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.resources.ResourceLocation;

// Describe one force passed into a hosted contraption diagram
public interface DiagramForceData {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the force group ID
    ResourceLocation groupId();

    // Get the local point X coordinate
    double pointX();

    // Get the local point Y coordinate
    double pointY();

    // Get the local point Z coordinate
    double pointZ();

    // Get the local force X component
    double forceX();

    // Get the local force Y component
    double forceY();

    // Get the local force Z component
    double forceZ();
}
