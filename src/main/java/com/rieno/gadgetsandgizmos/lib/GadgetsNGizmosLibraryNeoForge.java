package com.rieno.gadgetsandgizmos.lib;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.config.GadgetsNGizmosLibraryConfigs;
import com.rieno.gadgetsandgizmos.lib.discovery.SableSubLevelResidency;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

// Load library configuration and Sable residency on NeoForge
@Mod(GadgetsNGizmosLibrary.MOD_ID)
public final class GadgetsNGizmosLibraryNeoForge {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the Gadgets & Gizmos library on NeoForge
    public GadgetsNGizmosLibraryNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        GadgetsNGizmosLibraryConfigs.register(modContainer);
        SableSubLevelResidency.bootstrap();
    }
}
