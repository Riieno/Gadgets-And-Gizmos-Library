package com.rieno.gadgetsandgizmos.lib.tablet;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

// Give the active server one shared access point for tablet storage
public final class TabletStorageApi {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final TabletStorage UNAVAILABLE = new TabletStorage() {
        // Get the tablet
        @Override
        public CompoundTag tablet(UUID tabletId) {
            return new CompoundTag();
        }

        // Get the app
        @Override
        public CompoundTag app(UUID tabletId, ResourceLocation appId) {
            return new CompoundTag();
        }

        // Get the installed apps
        @Override
        public Set<ResourceLocation> installedApps(UUID tabletId) {
            return Set.of();
        }

        // Update the tablet
        @Override
        public CompoundTag updateTablet(UUID tabletId, UnaryOperator<CompoundTag> mutation) {
            return new CompoundTag();
        }

        // Update the app
        @Override
        public CompoundTag updateApp(UUID tabletId, ResourceLocation appId,
                                     UnaryOperator<CompoundTag> mutation) {
            return new CompoundTag();
        }

        // Set the installed
        @Override
        public boolean setInstalled(UUID tabletId, ResourceLocation appId, boolean installed) {
            return false;
        }
    };

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Shared storage
    private static volatile TabletStorage storage = UNAVAILABLE;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the tablet storage API
    private TabletStorageApi() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the storage
    public static TabletStorage storage() {
        return storage;
    }

    // Check if this is available
    public static boolean available() {
        return storage != UNAVAILABLE;
    }

    // Install the tablet storage API
    public static synchronized void install(TabletStorage next) {
        if (next == null) throw new IllegalArgumentException("Tablet storage cannot be null");
        storage = next;
    }

    // Uninstall the tablet storage API
    public static synchronized void uninstall(TabletStorage expected) {
        if (storage == expected) storage = UNAVAILABLE;
    }
}
