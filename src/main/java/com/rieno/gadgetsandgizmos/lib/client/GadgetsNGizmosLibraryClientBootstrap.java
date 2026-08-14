package com.rieno.gadgetsandgizmos.lib.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.client.render.AreaHighlightRenderTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

// Register the library's client render resources without loading them on a server
public final class GadgetsNGizmosLibraryClientBootstrap {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the Gadgets & Gizmos client bootstrap
    private GadgetsNGizmosLibraryClientBootstrap() {
    }

    // Register the Gadgets & Gizmos client bootstrap
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener((RegisterShadersEvent evt) -> {
            try {
                AreaHighlightRenderTypes.onRegisterShaders(evt);
            } catch (java.io.IOException err) {
                throw new RuntimeException("Failed to register area highlight shader", err);
            }
        });
    }
}
