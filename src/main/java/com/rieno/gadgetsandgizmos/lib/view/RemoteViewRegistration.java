package com.rieno.gadgetsandgizmos.lib.view;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// Register the library-owned remote view network contracts
@EventBusSubscriber(modid = "gadgetsngizmos", bus = EventBusSubscriber.Bus.MOD)
public final class RemoteViewRegistration{
    // Prevent construction of the registration holder
    private RemoteViewRegistration(){}
    // Register both logical directions on the common mod bus
    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent evt){
        var registrar = evt.registrar("1");
        registrar.playToServer(RemoteViewPayload.TYPE, RemoteViewPayload.STREAM_CODEC, RemoteViewPayload::handle);
        registrar.playToClient(RemoteViewStatePayload.TYPE, RemoteViewStatePayload.STREAM_CODEC, RemoteViewStatePayload::handle);
    }
}
