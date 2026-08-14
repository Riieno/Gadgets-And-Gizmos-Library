package com.rieno.gadgetsandgizmos.lib.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.neoforged.bus.api.IEventBus;

// Preserve the old client bootstrap name for addons compiled against the beta API
@Deprecated(forRemoval = false)
public final class CreateThrustersLibraryClientBootstrap {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the legacy client bootstrap
    private CreateThrustersLibraryClientBootstrap() {
    }

    // Register the Gadgets & Gizmos client bootstrap
    public static void register(IEventBus modEventBus) {
        GadgetsNGizmosLibraryClientBootstrap.register(modEventBus);
    }
}
