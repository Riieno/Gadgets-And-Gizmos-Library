package com.rieno.gadgetsandgizmos.lib.view;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.function.Consumer;

// Confirm the server aim without making first-person rendering wait for a block update
public record RemoteViewAimPayload(CompoundTag source, long sequence, ViewRig.State aim) implements CustomPacketPayload{
    public static final Type<RemoteViewAimPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gadgetsngizmos", "remote_view_aim"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RemoteViewAimPayload> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> {
                buf.writeNbt(msg.source()); buf.writeVarLong(msg.sequence());
                buf.writeDouble(msg.aim().pan()); buf.writeDouble(msg.aim().tilt()); buf.writeDouble(msg.aim().fov());
            }, buf -> new RemoteViewAimPayload(buf.readNbt(), buf.readVarLong(),
                    new ViewRig.State(buf.readDouble(), buf.readDouble(), buf.readDouble())));
    private static Consumer<RemoteViewAimPayload> client = msg -> {};
    public RemoteViewAimPayload{ source = source == null ? new CompoundTag() : source.copy(); }
    @Override public CompoundTag source(){ return source.copy(); }
    @Override public Type<? extends CustomPacketPayload> type(){ return TYPE; }
    public static void registerClientHandler(Consumer<RemoteViewAimPayload> handler){ client = java.util.Objects.requireNonNull(handler); }
    public static void handle(RemoteViewAimPayload msg, IPayloadContext ctx){ ctx.enqueueWork(() -> client.accept(msg)); }
}
