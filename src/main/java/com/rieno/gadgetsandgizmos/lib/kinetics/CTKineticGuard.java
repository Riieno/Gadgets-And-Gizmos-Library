package com.rieno.gadgetsandgizmos.lib.kinetics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;

import java.util.function.Predicate;

// Preserve the original kinetic guard API while the public library name changes
@Deprecated(forRemoval = false)
public final class CTKineticGuard {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the legacy kinetic guard API
    private CTKineticGuard() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Register one kinetic guard exception
    public static void registerGuardException(String key, Predicate<KineticBlockEntity> predicate) {
        GadgetsNGizmosKineticGuard.registerGuardException(key, predicate);
    }

    // Remove one kinetic guard exception
    public static boolean unregisterGuardException(String key) {
        return GadgetsNGizmosKineticGuard.unregisterGuardException(key);
    }

    // Check if held angles may be applied
    public static boolean shouldApplyHeldAngleTo(KineticBlockEntity blockEntity) {
        return GadgetsNGizmosKineticGuard.shouldApplyHeldAngleTo(blockEntity);
    }

    // Check if precise angles may propagate
    public static boolean shouldPropagatePreciseAngleTo(KineticBlockEntity blockEntity) {
        return GadgetsNGizmosKineticGuard.shouldPropagatePreciseAngleTo(blockEntity);
    }

    // Check if virtual kinetics may propagate
    public static boolean shouldUseVirtualKineticFor(KineticBlockEntity blockEntity, BlockPos neighborPos) {
        return GadgetsNGizmosKineticGuard.shouldUseVirtualKineticFor(blockEntity, neighborPos);
    }
}
