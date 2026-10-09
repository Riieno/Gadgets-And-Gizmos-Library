package com.rieno.gadgetsandgizmos.lib;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.config.GadgetsNGizmosLibraryConfigs;
import com.rieno.gadgetsandgizmos.lib.discovery.SableSubLevelResidency;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerRecipeCatalog;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
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
        com.rieno.gadgetsandgizmos.lib.inventory.ContainerAutomationPolicies.register();
        NeoForge.EVENT_BUS.addListener(GadgetsNGizmosLibraryNeoForge::onTagsUpdated);
        NeoForge.EVENT_BUS.addListener(GadgetsNGizmosLibraryNeoForge::onServerStarted);
        NeoForge.EVENT_BUS.addListener(GadgetsNGizmosLibraryNeoForge::onServerStopped);
    }

    // Drop resolved tag members whenever datapacks update them
    private static void onTagsUpdated(TagsUpdatedEvent evt){
        WorkerRecipeCatalog.invalidate();
    }

    // Warm the worker recipe graph across ticks after recipes and tags finish loading
    private static void onServerStarted(ServerStartedEvent evt){
        WorkerRecipeCatalog.prepareLookup(evt.getServer().overworld());
    }

    // Do not retain recipe managers from stopped worlds
    private static void onServerStopped(ServerStoppedEvent evt){
        WorkerRecipeCatalog.invalidate();
    }
}
