package com.rieno.gadgetsandgizmos.lib.virtualkinetics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import org.jetbrains.annotations.Nullable;

// Link a virtual kinetic instance back to its real provider and slot
public interface VirtualKineticBlockEntity {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the virtual kinetic parent
    @Nullable
    KineticBlockEntity ct$getVirtualKineticParent();

    // Get the virtual kinetic slot
    int ct$getVirtualKineticSlot();

    // Get the virtual kinetic rotation configuration
    IRotate ct$getVirtualKineticRotationConfiguration();
}
