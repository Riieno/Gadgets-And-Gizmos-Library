package com.rieno.gadgetsandgizmos.lib.client.tablet;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

// Keep optional client renderers separate from server tablet app handlers
public final class TabletAppClientRegistry {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<ResourceLocation, TabletAppClientRenderer> RENDERERS = new LinkedHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the tablet app client
    private TabletAppClientRegistry() {
    }

    // Register the tablet app client
    public static synchronized void register(ResourceLocation appId, TabletAppClientRenderer renderer) {
        Objects.requireNonNull(appId, "appId");
        Objects.requireNonNull(renderer, "renderer");
        if (RENDERERS.putIfAbsent(appId, renderer) != null) {
            throw new IllegalStateException("Tablet app renderer already registered: " + appId);
        }
    }

    // Register the tablet app client if absent
    public static synchronized boolean registerIfAbsent(ResourceLocation appId,
                                                        TabletAppClientRenderer renderer) {
        if (RENDERERS.containsKey(appId)) return false;
        register(appId, renderer);
        return true;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Remove the tablet app client
    public static synchronized boolean unregister(ResourceLocation appId) {
        return appId != null && RENDERERS.remove(appId) != null;
    }

    // Get the renderer
    public static synchronized @Nullable TabletAppClientRenderer renderer(ResourceLocation appId) {
        return appId == null ? null : RENDERERS.get(appId);
    }
}
