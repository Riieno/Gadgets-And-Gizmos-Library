package com.rieno.gadgetsandgizmos.lib.kinetics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

// Expose the state and controls shared by multi-head bearing implementations
public interface BearingHeadAccess {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get one head direction
    Direction getHeadDirection(BearingHead head);

    // Get one current head angle
    double getHeadAngle(BearingHead head);

    // Get one target head angle
    double getHeadTargetAngle(BearingHead head);

    // Get one interpolated head angle
    double getInterpolatedHeadAngle(BearingHead head, float partialTicks);

    // Get one minimum head angle
    double getMinAngle(BearingHead head);

    // Get one maximum head angle
    double getMaxAngle(BearingHead head);

    // Set one head angle range
    void setAngleRange(BearingHead head, double minAngleDeg, double maxAngleDeg);

    // Set one head target angle
    void setHeadTargetAngle(BearingHead head, double angleDeg);

    // Clear one head target override
    void clearHeadTargetOverride(BearingHead head);

    // Check if one head has a target override
    boolean hasHeadTargetOverride(BearingHead head);

    // Get one mounted head block position
    @Nullable BlockPos getMountedBlockPos(BearingHead head);

    // Get one mounted head sublevel ID
    @Nullable UUID getMountedSubLevelId(BearingHead head);

    // Check if one mounted head assembly is present
    boolean isMountedAssemblyPresent(BearingHead head);

    // Set one mounted head assembly state
    boolean setMountedAssemblyPresent(BearingHead head, boolean assembled);
}
