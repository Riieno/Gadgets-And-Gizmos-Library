package com.rieno.gadgetsandgizmos.lib.view;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.function.Consumer;

// Synchronize remote view ownership through a side-safe client callback
public record RemoteViewStatePayload(CompoundTag source, boolean active) implements CustomPacketPayload{
    public static final Type<RemoteViewStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gadgetsngizmos", "remote_view_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RemoteViewStatePayload> STREAM_CODEC =
            StreamCodec.of(RemoteViewStatePayload::encode, RemoteViewStatePayload::decode);
    // Install the client callback without loading client types on a server
    private static Consumer<RemoteViewStatePayload> clientHandler = msg -> {};
    // Retain a separate reference envelope
    public RemoteViewStatePayload{ source = source.copy(); }
    // Return a separate source envelope
    @Override
    public CompoundTag source(){ return source.copy(); }
    // Install the handler during physical client setup
    public static void registerClientHandler(Consumer<RemoteViewStatePayload> handler){
        clientHandler = java.util.Objects.requireNonNull(handler);
    }
    // Identify the state packet
    @Override
    public Type<? extends CustomPacketPayload> type(){ return TYPE; }
    // Encode the source and ownership state
    private static void encode(RegistryFriendlyByteBuf buf, RemoteViewStatePayload msg){
        buf.writeNbt(msg.source);
        buf.writeBoolean(msg.active);
    }
    // Decode the source and ownership state
    private static RemoteViewStatePayload decode(RegistryFriendlyByteBuf buf){
        CompoundTag tag = buf.readNbt();
        return new RemoteViewStatePayload(tag == null ? new CompoundTag() : tag, buf.readBoolean());
    }
    // Deliver state on the owning client thread
    public static void handle(RemoteViewStatePayload msg, IPayloadContext ctx){
        ctx.enqueueWork(() -> clientHandler.accept(msg));
    }
}
