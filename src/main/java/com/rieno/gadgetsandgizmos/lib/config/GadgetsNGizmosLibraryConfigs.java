package com.rieno.gadgetsandgizmos.lib.config;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

// Expose the server settings used by the library's global compatibility patches
public final class GadgetsNGizmosLibraryConfigs {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Server SERVER;
    public static final ModConfigSpec SERVER_SPEC;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Load the library server settings
    static {
        ModConfigSpec.Builder serverBuilder = new ModConfigSpec.Builder();
        SERVER = new Server(serverBuilder);
        SERVER_SPEC = serverBuilder.build();
    }

    // Initialize the library configuration API
    private GadgetsNGizmosLibraryConfigs() {
    }

    // Register the library server configuration
    public static void register(ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, SERVER_SPEC,
                "gadgetsngizmos-server.toml");
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if the global held angle mixin is enabled
    public static boolean enableGlobalHeldAngleMixin() {
        return SERVER.enableGlobalHeldAngleMixin.get();
    }

    // Check if global precise angle propagation is enabled
    public static boolean enableGlobalPreciseAnglePropagation() {
        return SERVER.enableGlobalPreciseAnglePropagation.get();
    }

    // Check if global virtual kinetic propagation is enabled
    public static boolean enableGlobalVirtualKineticPropagation() {
        return SERVER.enableGlobalVirtualKineticPropagation.get();
    }

    // Check if kinetic guard decisions should be logged
    public static boolean logKineticGuardDecisions() {
        return SERVER.logKineticGuardDecisions.get();
    }

    // Store the server configuration
    public static final class Server {
        // Enable global held angle mixin
        public final ModConfigSpec.BooleanValue enableGlobalHeldAngleMixin;
        // Enable global precise angle propagation
        public final ModConfigSpec.BooleanValue enableGlobalPreciseAnglePropagation;
        // Enable global virtual kinetic propagation
        public final ModConfigSpec.BooleanValue enableGlobalVirtualKineticPropagation;
        // Log kinetic guard decisions
        public final ModConfigSpec.BooleanValue logKineticGuardDecisions;

        // Initialize the server settings
        private Server(ModConfigSpec.Builder builder) {
            builder.comment("Gadgets & Gizmos library safeguards").push("server");
            enableGlobalHeldAngleMixin = builder
                    .comment("Enable global held-angle mixin behavior on kinetic entities")
                    .define("enableGlobalHeldAngleMixin", true);
            enableGlobalPreciseAnglePropagation = builder
                    .comment("Enable precise-angle propagation graph writes to connected kinetics")
                    .define("enableGlobalPreciseAnglePropagation", true);
            enableGlobalVirtualKineticPropagation = builder
                    .comment("Enable virtual-kinetics neighbor and slot propagation hooks")
                    .define("enableGlobalVirtualKineticPropagation", true);
            logKineticGuardDecisions = builder
                    .comment("Log one-time guard rejections for kinetic safeguard troubleshooting")
                    .define("logKineticGuardDecisions", false);
            builder.pop();
        }
    }
}
