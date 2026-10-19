package com.rieno.gadgetsandgizmos.lib.view;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Sequence predicted input while preserving the existing unsequenced control packet
public record RemoteViewInputPayload(long sequence, double pan, double tilt, double zoom) implements CustomPacketPayload{
    public static final Type<RemoteViewInputPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gadgetsngizmos", "remote_view_predicted_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RemoteViewInputPayload> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> { buf.writeVarLong(msg.sequence()); buf.writeDouble(msg.pan()); buf.writeDouble(msg.tilt()); buf.writeDouble(msg.zoom()); },
            buf -> new RemoteViewInputPayload(buf.readVarLong(), buf.readDouble(), buf.readDouble(), buf.readDouble()));
    @Override public Type<? extends CustomPacketPayload> type(){ return TYPE; }
    public static void handle(RemoteViewInputPayload msg, IPayloadContext ctx){
        ctx.enqueueWork(() -> {
            if(ctx.player() instanceof ServerPlayer player) RemoteViewSessions.input(player, msg);
        });
    }
}
