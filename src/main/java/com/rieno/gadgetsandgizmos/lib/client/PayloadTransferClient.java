package com.rieno.gadgetsandgizmos.lib.client;

import com.rieno.gadgetsandgizmos.lib.GadgetsNGizmosLibrary;
import com.rieno.gadgetsandgizmos.lib.network.PayloadTransferChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

// Pace client uploads without linking client classes into the common transport
@EventBusSubscriber(modid = GadgetsNGizmosLibrary.MOD_ID, value = Dist.CLIENT)
public final class PayloadTransferClient{
    // Release the previous world's transfers before using another connection
    private static Connection prevConnection;

    // Send through the consuming mod's registered channel
    public static void send(PayloadTransferChannel channel, CustomPacketPayload payload){
        var listener = Minecraft.getInstance().getConnection();
        if(listener != null) channel.sendToServer(listener.getConnection(), listener.registryAccess(), payload);
    }

    // Limit upload work to two chunks per client tick
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post evt){
        var listener = Minecraft.getInstance().getConnection();
        Connection connection = listener == null ? null : listener.getConnection();
        if(prevConnection != null && prevConnection != connection) PayloadTransferChannel.clear(prevConnection);
        prevConnection = connection;
        if(connection != null) PayloadTransferChannel.flush(connection);
    }
}
