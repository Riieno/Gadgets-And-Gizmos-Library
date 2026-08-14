package com.rieno.gadgetsandgizmos.lib;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.compat.PhysicsStaffPowerTracker;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// Advance and shut down Physics Staff power tracking with the server lifecycle
@EventBusSubscriber(modid = GadgetsNGizmosLibrary.MOD_ID)
public final class PhysicsStaffPowerEvents {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the physics staff power events
    private PhysicsStaffPowerEvents() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the post-server tick
    @SubscribeEvent
    public static void postServerTick(ServerTickEvent.Post event) {
        PhysicsStaffPowerTracker.get(event.getServer()).tick(event.getServer());
    }

    // Handle server shutdown
    @SubscribeEvent
    public static void serverStopping(ServerStoppingEvent event) {
        PhysicsStaffPowerTracker.get(event.getServer()).beginShutdown(event.getServer());
    }
}
