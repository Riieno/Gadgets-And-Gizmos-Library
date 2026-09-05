package com.rieno.gadgetsandgizmos.lib.scm;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

// Store one stable ship-local block target used during SCM discovery
public record ScmTarget(
        @Nullable UUID subLevelId,
        BlockPos blockPosition,
        String blockId,
        String label,
        @Nullable BlockPos signalPosition,
        @Nullable Direction signalFace,
        String controlChannelId
) {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the SCM target
    public ScmTarget(@Nullable UUID subLevelId, BlockPos blockPosition,
                     String blockId, String label) {
        this(subLevelId, blockPosition, blockId, label, null, null, "");
    }

    // Initialize the SCM target with a linked face
    public ScmTarget(@Nullable UUID subLevelId, BlockPos blockPosition,
                     String blockId, String label,
                     @Nullable BlockPos signalPosition,
                     @Nullable Direction signalFace) {
        this(subLevelId, blockPosition, blockId, label,
                signalPosition, signalFace, "");
    }

    // Initialize the SCM target
    public ScmTarget {
        blockPosition = blockPosition == null ? BlockPos.ZERO : blockPosition.immutable();
        blockId = blockId == null ? "minecraft:air" : blockId.strip();
        label = label == null ? "" : label.strip();
        signalPosition = signalPosition == null ? null : signalPosition.immutable();
        controlChannelId = controlChannelId == null ? "" : controlChannelId.strip();
        if (signalPosition == null) {
            signalFace = null;
            controlChannelId = "";
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this uses face control
    public boolean usesFaceControl() {
        return signalPosition != null && signalFace != null;
    }

    // Check whether this target was bound to one exact SCM control channel
    public boolean usesControlChannel() {
        return usesFaceControl() && !controlChannelId.isBlank();
    }

    // Get the stable id
    public String stableId() {
        return (subLevelId == null ? "world" : subLevelId.toString())
                + ":" + blockPosition.asLong() + ":" + blockId
                + (usesFaceControl()
                ? ":face:" + signalPosition.asLong() + ":" + signalFace.getSerializedName()
                : ":block");
    }
}
