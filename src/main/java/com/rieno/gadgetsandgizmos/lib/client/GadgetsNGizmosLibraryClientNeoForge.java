package com.rieno.gadgetsandgizmos.lib.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.GadgetsNGizmosLibrary;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

// Load library render resources only on a physical client
@Mod(value = GadgetsNGizmosLibrary.MOD_ID, dist = Dist.CLIENT)
public final class GadgetsNGizmosLibraryClientNeoForge {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the Gadgets & Gizmos library client
    public GadgetsNGizmosLibraryClientNeoForge(IEventBus modEventBus) {
        GadgetsNGizmosLibraryClientBootstrap.register(modEventBus);
    }
}
