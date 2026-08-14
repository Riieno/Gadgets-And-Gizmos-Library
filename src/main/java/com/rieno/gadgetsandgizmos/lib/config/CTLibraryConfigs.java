package com.rieno.gadgetsandgizmos.lib.config;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.ModConfigSpec;

// Preserve the original configuration type while the public library name changes
@Deprecated(forRemoval = false)
public final class CTLibraryConfigs {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Server SERVER = new Server(GadgetsNGizmosLibraryConfigs.SERVER);
    public static final ModConfigSpec SERVER_SPEC = GadgetsNGizmosLibraryConfigs.SERVER_SPEC;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the legacy configuration type
    private CTLibraryConfigs() {
    }

    // Register the Gadgets & Gizmos library configuration
    public static void register(ModContainer modContainer) {
        GadgetsNGizmosLibraryConfigs.register(modContainer);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if the global held angle mixin is enabled
    public static boolean enableGlobalHeldAngleMixin() {
        return GadgetsNGizmosLibraryConfigs.enableGlobalHeldAngleMixin();
    }

    // Check if global precise angle propagation is enabled
    public static boolean enableGlobalPreciseAnglePropagation() {
        return GadgetsNGizmosLibraryConfigs.enableGlobalPreciseAnglePropagation();
    }

    // Check if global virtual kinetic propagation is enabled
    public static boolean enableGlobalVirtualKineticPropagation() {
        return GadgetsNGizmosLibraryConfigs.enableGlobalVirtualKineticPropagation();
    }

    // Check if kinetic guard decisions should be logged
    public static boolean logKineticGuardDecisions() {
        return GadgetsNGizmosLibraryConfigs.logKineticGuardDecisions();
    }

    // Preserve the original server settings view
    @Deprecated(forRemoval = false)
    public static final class Server {
        // Enable global held angle mixin
        public final ModConfigSpec.BooleanValue enableGlobalHeldAngleMixin;
        // Enable global precise angle propagation
        public final ModConfigSpec.BooleanValue enableGlobalPreciseAnglePropagation;
        // Enable global virtual kinetic propagation
        public final ModConfigSpec.BooleanValue enableGlobalVirtualKineticPropagation;
        // Log kinetic guard decisions
        public final ModConfigSpec.BooleanValue logKineticGuardDecisions;

        // Wrap the renamed server settings
        private Server(GadgetsNGizmosLibraryConfigs.Server server) {
            enableGlobalHeldAngleMixin = server.enableGlobalHeldAngleMixin;
            enableGlobalPreciseAnglePropagation = server.enableGlobalPreciseAnglePropagation;
            enableGlobalVirtualKineticPropagation = server.enableGlobalVirtualKineticPropagation;
            logKineticGuardDecisions = server.logKineticGuardDecisions;
        }
    }
}
