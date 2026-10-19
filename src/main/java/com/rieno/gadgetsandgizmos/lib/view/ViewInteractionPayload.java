package com.rieno.gadgetsandgizmos.lib.view;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.function.Consumer;

// Replay native client use only after the server has selected and authorized the hit
public record ViewInteractionPayload(CompoundTag source, CompoundTag target, boolean useBlock) implements CustomPacketPayload{
    public static final Type<ViewInteractionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("gadgetsngizmos", "view_interaction"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ViewInteractionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> { buf.writeNbt(msg.source); buf.writeNbt(msg.target); buf.writeBoolean(msg.useBlock); },
            buf -> new ViewInteractionPayload(read(buf), read(buf), buf.readBoolean()));
    private static Consumer<ViewInteractionPayload> client = msg -> {};
    public ViewInteractionPayload{ source = source.copy(); target = target.copy(); }
    @Override public CompoundTag source(){ return source.copy(); }
    @Override public CompoundTag target(){ return target.copy(); }
    private static CompoundTag read(RegistryFriendlyByteBuf buf){ var tag = buf.readNbt(); return tag == null ? new CompoundTag() : tag; }
    public static void registerClientHandler(Consumer<ViewInteractionPayload> handler){ client = java.util.Objects.requireNonNull(handler); }
    @Override public Type<? extends CustomPacketPayload> type(){ return TYPE; }
    public static void handle(ViewInteractionPayload msg, IPayloadContext ctx){ ctx.enqueueWork(() -> client.accept(msg)); }
}
