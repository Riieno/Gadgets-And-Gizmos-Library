package com.rieno.gadgetsandgizmos.lib.chat;

import com.rieno.gadgetsandgizmos.lib.GadgetsNGizmosLibrary;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

// Observe accepted player chat and clean up subscriptions with their server
@EventBusSubscriber(modid = GadgetsNGizmosLibrary.MOD_ID)
public final class ServerChatEvents{
    private ServerChatEvents(){}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void chat(ServerChatEvent event){
        if(event.isCanceled()) return;
        var player = event.getPlayer();
        if(ServerChatApi.receivePlayerChat(player.getServer(),
                new ServerChatApi.Message(event.getRawText(), player.getUUID(), event.getUsername()))){
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event){
        ServerChatApi.clear(event.getServer());
    }
}

