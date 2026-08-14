package com.rieno.gadgetsandgizmos.lib.menuconfig;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

// Save the level and position header used to open a sub-level-aware menu
public record MenuOpenHeader(BlockPos pos, @Nullable UUID subLevelId) {

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Decode the menu open header
    public static MenuOpenHeader decode(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        UUID subLevelId = buf.readBoolean() ? buf.readUUID() : null;
        return new MenuOpenHeader(pos, subLevelId);
    }

    // Encode the menu open header
    public static void encode(RegistryFriendlyByteBuf buf, BlockPos pos, @Nullable UUID subLevelId) {
        buf.writeBlockPos(pos);
        buf.writeBoolean(subLevelId != null);
        if (subLevelId != null) {
            buf.writeUUID(subLevelId);
        }
    }
}
