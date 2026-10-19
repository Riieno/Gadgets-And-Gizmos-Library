package com.rieno.gadgetsandgizmos.lib.view;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// A native hold started through a feed needs raw mouse input from the authenticated lens
public record ViewInteractionTakeoverPayload() implements CustomPacketPayload{
    public static final Type<ViewInteractionTakeoverPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("gadgetsngizmos", "view_interaction_takeover"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ViewInteractionTakeoverPayload> STREAM_CODEC = StreamCodec.unit(new ViewInteractionTakeoverPayload());
    @Override public Type<? extends CustomPacketPayload> type(){ return TYPE; }
    public static void handle(ViewInteractionTakeoverPayload msg, IPayloadContext ctx){
        ctx.enqueueWork(() -> {
            if(!(ctx.player() instanceof ServerPlayer player)) return;
            var cursor = ViewInteractionContext.cursor(player);
            if(cursor != null) RemoteViewSessions.open(player, cursor.source());
        });
    }
}
