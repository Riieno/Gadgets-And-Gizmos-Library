package com.rieno.gadgetsandgizmos.lib.kinetics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.Direction;

// Expose the held angle state added to a Create kinetic block entity
public interface HeldKineticAngleAccess {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Set the held angle
    boolean ct$setHeldAngle(Direction.Axis axis, float angleDegrees);

    // Clear the held angles
    boolean ct$clearHeldAngles();

    // Check if this has held angle
    boolean ct$hasHeldAngle(Direction.Axis axis);

    // Get the absolute rotation angle
    float ct$getAbsoluteRotationAngle(Direction.Axis axis);
}
