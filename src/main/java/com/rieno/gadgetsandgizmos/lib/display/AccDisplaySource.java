package com.rieno.gadgetsandgizmos.lib.display;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

// Supply an external ACC display frame for one registered block type
public interface AccDisplaySource {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the frame
    CompoundTag frame(BlockEntity src, int pixelWidth, int pixelHeight);

    // Handle the ACC display source
    default boolean interact(BlockEntity src, double horizontal, double vertical, int mouseButton) {
        return false;
    }

    // Submit display input
    default boolean input(BlockEntity src, String action, double horizontal, double vertical,
                          int val) {
        return false;
    }
}
