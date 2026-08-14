package com.rieno.gadgetsandgizmos.lib.virtualkinetics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// Supply the virtual kinetic block entities owned by one real block entity
public interface VirtualKineticProvider {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the virtual kinetics
    List<KineticBlockEntity> ct$getVirtualKinetics();

    // Get the virtual kinetic
    @Nullable
    default KineticBlockEntity ct$getVirtualKinetic(int slot) {
        List<KineticBlockEntity> kinetics = ct$getVirtualKinetics();
        if (slot < 0 || slot >= kinetics.size()) {
            return null;
        }
        return kinetics.get(slot);
    }

    // Get the virtual kinetic count
    default int ct$getVirtualKineticCount() {
        return ct$getVirtualKinetics().size();
    }

    // Get the virtual kinetic save name
    default String ct$getVirtualKineticSaveName(int slot) {
        return "CTVirtualKinetic" + slot;
    }
}
