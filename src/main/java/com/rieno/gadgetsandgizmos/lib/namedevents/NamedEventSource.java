package com.rieno.gadgetsandgizmos.lib.namedevents;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// Identify the live endpoint which published one named event
public record NamedEventSource(
        MinecraftServer server,
        ResourceKey<Level> dimension,
        Vec3 position,
        String endpointId
) {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the named event source
    public NamedEventSource {
        if (server == null) {
            throw new IllegalArgumentException("Named Event sources require a server");
        }
        if (dimension == null) {
            throw new IllegalArgumentException("Named Event sources require a dimension");
        }
        endpointId = endpointId == null || endpointId.isBlank()
                ? "unknown"
                : endpointId.strip();
    }
}
