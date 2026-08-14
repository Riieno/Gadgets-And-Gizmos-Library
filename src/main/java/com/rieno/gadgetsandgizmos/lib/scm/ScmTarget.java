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
        @Nullable Direction signalFace
) {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the SCM target
    public ScmTarget(@Nullable UUID subLevelId, BlockPos blockPosition,
                     String blockId, String label) {
        this(subLevelId, blockPosition, blockId, label, null, null);
    }

    // Initialize the SCM target
    public ScmTarget {
        blockPosition = blockPosition == null ? BlockPos.ZERO : blockPosition.immutable();
        blockId = blockId == null ? "minecraft:air" : blockId.strip();
        label = label == null ? "" : label.strip();
        signalPosition = signalPosition == null ? null : signalPosition.immutable();
        if (signalPosition == null) {
            signalFace = null;
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

    // Get the stable id
    public String stableId() {
        return (subLevelId == null ? "world" : subLevelId.toString())
                + ":" + blockPosition.asLong() + ":" + blockId
                + (usesFaceControl()
                ? ":face:" + signalPosition.asLong() + ":" + signalFace.getSerializedName()
                : ":block");
    }
}
