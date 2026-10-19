package com.rieno.gadgetsandgizmos.lib.view;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Native GUI and drag packets keep the previously authenticated target alive
public record ViewInteractionKeepAlivePayload() implements CustomPacketPayload{
    public static final Type<ViewInteractionKeepAlivePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("gadgetsngizmos", "view_interaction_keepalive"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ViewInteractionKeepAlivePayload> STREAM_CODEC = StreamCodec.unit(new ViewInteractionKeepAlivePayload());
    @Override public Type<? extends CustomPacketPayload> type(){ return TYPE; }
    public static void handle(ViewInteractionKeepAlivePayload msg, IPayloadContext ctx){
        ctx.enqueueWork(() -> { if(ctx.player() instanceof ServerPlayer player) ViewInteractionContext.keepAlive(player); });
    }
}
