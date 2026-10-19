package com.rieno.gadgetsandgizmos.lib.view;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Route discrete lens actions only through an already authorized remote session
public record RemoteViewActionPayload(Action action) implements CustomPacketPayload{
    public enum Action{ FLASHLIGHT, RAYCAST, INTERACT }
    public static final Type<RemoteViewActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gadgetsngizmos", "remote_view_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RemoteViewActionPayload> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> buf.writeEnum(msg.action()), buf -> new RemoteViewActionPayload(buf.readEnum(Action.class)));

    @Override public Type<? extends CustomPacketPayload> type(){ return TYPE; }

    public static void handle(RemoteViewActionPayload msg, IPayloadContext ctx){
        ctx.enqueueWork(() -> {
            if(!(ctx.player() instanceof ServerPlayer player)) return;
            if(msg.action() == Action.FLASHLIGHT){
                RemoteViewSessions.toggleFlashlight(player);
            }else if(msg.action() == Action.INTERACT){
                RemoteViewSessions.interact(player);
            }else{
                var result = RemoteViewSessions.raycast(player);
                var ref = RemoteViewSessions.source(player);
                if(result != null && ref != null) PacketDistributor.sendToPlayer(player,
                        new RemoteViewRaycastPayload(ref.toTag(), result.hit(), result.position(), result.distance()));
            }
        });
    }
}
