package com.rieno.gadgetsandgizmos.lib;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.access.ProtectionBypass;
import com.rieno.gadgetsandgizmos.lib.config.GadgetsNGizmosLibraryConfigs;
import com.rieno.gadgetsandgizmos.lib.discovery.SableSubLevelResidency;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerRecipeCatalog;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.server.level.ServerPlayer;
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
        com.rieno.gadgetsandgizmos.lib.color.ColorEffectParticles.register(modEventBus);
        GadgetsNGizmosLibraryConfigs.register(modContainer);
        SableSubLevelResidency.bootstrap();
        com.rieno.gadgetsandgizmos.lib.item.TimedItemTransformations.register();
        NeoForge.EVENT_BUS.addListener(GadgetsNGizmosLibraryNeoForge::onTagsUpdated);
        NeoForge.EVENT_BUS.addListener(GadgetsNGizmosLibraryNeoForge::onServerStarted);
        NeoForge.EVENT_BUS.addListener(GadgetsNGizmosLibraryNeoForge::onServerStopped);
        NeoForge.EVENT_BUS.addListener(GadgetsNGizmosLibraryNeoForge::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(GadgetsNGizmosLibraryNeoForge::onChunkUnloaded);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,
                GadgetsNGizmosLibraryNeoForge::onServerTick);
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
        ProtectionBypass.clear(evt.getServer());
        com.rieno.gadgetsandgizmos.lib.inventory.ContainerStorageNetworks.clear(evt.getServer());
        com.rieno.gadgetsandgizmos.lib.network.BlockEntityDataSync.clear(evt.getServer());
    }

    // Reconnecting players must enable the bypass again
    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent evt){
        if(evt.getEntity() instanceof ServerPlayer player) ProtectionBypass.clear(player);
    }

    // Reject cached storage access before a world or Sable plot chunk unloads
    private static void onChunkUnloaded(ChunkEvent.Unload evt){
        if(!evt.getLevel().isClientSide()) com.rieno.gadgetsandgizmos.lib.inventory.ContainerStorageNetworks.invalidate();
    }

    // Coalesce state changes without delaying them beyond the issuing server tick
    private static void onServerTick(ServerTickEvent.Post evt){
        com.rieno.gadgetsandgizmos.lib.network.BlockEntityDataSync.flush(evt.getServer());
    }
}
