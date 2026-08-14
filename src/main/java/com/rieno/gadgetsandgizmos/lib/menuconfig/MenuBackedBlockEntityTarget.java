package com.rieno.gadgetsandgizmos.lib.menuconfig;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.UUID;

// Expose the resolved block entity and coordinates behind a configuration menu
public interface MenuBackedBlockEntityTarget<B extends BlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the menu config target pos
    BlockPos getMenuConfigTargetPos();

    // Get the menu config target sublevel id
    default UUID getMenuConfigTargetSubLevelId() {
        return null;
    }

    // Get the menu config target block entity
    B getMenuConfigTargetBlockEntity();
}
