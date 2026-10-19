package com.rieno.gadgetsandgizmos.lib.view;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Forward bounded mouse input only to the player's active view session
public record RemoteViewPayload(double pan, double tilt, double zoom, boolean exit) implements CustomPacketPayload{
    public static final Type<RemoteViewPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gadgetsngizmos", "remote_view_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RemoteViewPayload> STREAM_CODEC =
            StreamCodec.of(RemoteViewPayload::encode, RemoteViewPayload::decode);
    // Identify the input packet
    @Override
    public Type<? extends CustomPacketPayload> type(){ return TYPE; }
    // Encode one control sample
    private static void encode(RegistryFriendlyByteBuf buf, RemoteViewPayload msg){
        buf.writeDouble(msg.pan);
        buf.writeDouble(msg.tilt);
        buf.writeDouble(msg.zoom);
        buf.writeBoolean(msg.exit);
    }
    // Decode one control sample
    private static RemoteViewPayload decode(RegistryFriendlyByteBuf buf){
        return new RemoteViewPayload(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readBoolean());
    }
    // Apply input on the owning server thread
    public static void handle(RemoteViewPayload msg, IPayloadContext ctx){
        ctx.enqueueWork(() -> {
            if(ctx.player() instanceof ServerPlayer player) RemoteViewSessions.input(player, msg);
        });
    }
}
