package com.rieno.gadgetsandgizmos.lib.graph;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.GadgetsNGizmosLibrary;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

// Define the common services a block-entity graph host may expose
public final class GraphHostServices {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final GraphServiceKey<BlockEntity> BLOCK_ENTITY = new GraphServiceKey<>(
            ResourceLocation.fromNamespaceAndPath(GadgetsNGizmosLibrary.MOD_ID, "block_entity"),
            BlockEntity.class);

    public static final GraphServiceKey<GraphNodeOutputs> NODE_OUTPUTS = new GraphServiceKey<>(
            ResourceLocation.fromNamespaceAndPath(GadgetsNGizmosLibrary.MOD_ID, "node_outputs"),
            GraphNodeOutputs.class);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the graph host services
    private GraphHostServices() {
    }
}
