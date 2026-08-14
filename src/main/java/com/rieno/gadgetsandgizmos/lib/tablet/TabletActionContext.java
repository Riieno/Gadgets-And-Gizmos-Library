package com.rieno.gadgetsandgizmos.lib.tablet;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

// Give a tablet action its player, item and current or placed target
public record TabletActionContext(ServerPlayer player, ItemStack tablet,
                                  @Nullable UUID subLevelId, @Nullable BlockPos blockPos,
                                  boolean placedSource, @Nullable UUID sourceTabletId,
                                  @Nullable UUID sourceSubLevelId, @Nullable BlockPos sourceBlockPos) {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the tablet action context
    public TabletActionContext(ServerPlayer player, ItemStack tablet,
                               @Nullable UUID subLevelId, @Nullable BlockPos blockPos) {
        this(player, tablet, subLevelId, blockPos, false, null, null, null);
    }

    // Initialize the tablet action context
    public TabletActionContext {
        blockPos = blockPos == null ? null : blockPos.immutable();
        sourceBlockPos = sourceBlockPos == null ? null : sourceBlockPos.immutable();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if the tablet app context is placed
    public boolean placed() {
        return placedSource;
    }
}
