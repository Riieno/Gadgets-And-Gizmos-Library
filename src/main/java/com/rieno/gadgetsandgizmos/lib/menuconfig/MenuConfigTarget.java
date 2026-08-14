package com.rieno.gadgetsandgizmos.lib.menuconfig;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

// Identify a root or Sable-local block entity opened by a configuration menu
public record MenuConfigTarget(BlockPos pos, UUID subLevelId) {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final StreamCodec<RegistryFriendlyByteBuf, MenuConfigTarget> STREAM_CODEC = StreamCodec.of(
            MenuConfigTarget::encode,
            MenuConfigTarget::decode);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the menu config target
    public static MenuConfigTarget of(BlockPos pos) {
        return new MenuConfigTarget(pos.immutable(), null);
    }

    // Create the menu config target
    public static MenuConfigTarget of(BlockPos pos, UUID subLevelId) {
        return new MenuConfigTarget(pos.immutable(), subLevelId);
    }

    // Encode the menu config target
    private static void encode(RegistryFriendlyByteBuf buf, MenuConfigTarget target) {

        buf.writeBlockPos(target.pos());
        buf.writeBoolean(target.subLevelId() != null);
        if (target.subLevelId() != null) {
            buf.writeUUID(target.subLevelId());
        }
    }

    // Decode the menu config target
    private static MenuConfigTarget decode(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        UUID subLevelId = buf.readBoolean() ? buf.readUUID() : null;
        return new MenuConfigTarget(pos, subLevelId);
    }
}
