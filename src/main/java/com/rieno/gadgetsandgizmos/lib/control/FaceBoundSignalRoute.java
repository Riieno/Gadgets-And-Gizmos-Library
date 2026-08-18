package com.rieno.gadgetsandgizmos.lib.control;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.Objects;

// Resolve a signal proxy to only the block attached behind its selected face
public record FaceBoundSignalRoute(BlockPos proxyPos, Direction face) {
    // Initialize the face bound signal route
    public FaceBoundSignalRoute {
        proxyPos = Objects.requireNonNull(proxyPos, "Proxy position").immutable();
        face = Objects.requireNonNull(face, "Proxy face");
    }

    // Get the attached target position
    public BlockPos attachedPos() {
        return proxyPos.relative(face.getOpposite());
    }

    // Check if the receiver is the attached target
    public boolean matches(BlockPos receiverPos) {
        return receiverPos != null && attachedPos().equals(receiverPos);
    }
}
