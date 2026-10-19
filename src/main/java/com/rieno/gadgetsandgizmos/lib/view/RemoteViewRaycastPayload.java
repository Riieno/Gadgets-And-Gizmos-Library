package com.rieno.gadgetsandgizmos.lib.view;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Objects;
import java.util.function.Consumer;

// Return a small ray result without exposing block entities or graph storage to the client
public record RemoteViewRaycastPayload(CompoundTag source, boolean hit, Vec3 position, double distance) implements CustomPacketPayload{
    public static final Type<RemoteViewRaycastPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gadgetsngizmos", "remote_view_raycast"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RemoteViewRaycastPayload> STREAM_CODEC =
            StreamCodec.of(RemoteViewRaycastPayload::encode, RemoteViewRaycastPayload::decode);
    private static Consumer<RemoteViewRaycastPayload> clientHandler = msg -> {};

    public RemoteViewRaycastPayload{ source = source.copy(); }
    @Override public CompoundTag source(){ return source.copy(); }
    @Override public Type<? extends CustomPacketPayload> type(){ return TYPE; }

    public static void registerClientHandler(Consumer<RemoteViewRaycastPayload> handler){
        clientHandler = Objects.requireNonNull(handler);
    }
    private static void encode(RegistryFriendlyByteBuf buf, RemoteViewRaycastPayload msg){
        buf.writeNbt(msg.source); buf.writeBoolean(msg.hit);
        buf.writeDouble(msg.position.x); buf.writeDouble(msg.position.y); buf.writeDouble(msg.position.z);
        buf.writeDouble(msg.distance);
    }
    private static RemoteViewRaycastPayload decode(RegistryFriendlyByteBuf buf){
        CompoundTag source = buf.readNbt();
        return new RemoteViewRaycastPayload(source == null ? new CompoundTag() : source, buf.readBoolean(),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readDouble());
    }
    public static void handle(RemoteViewRaycastPayload msg, IPayloadContext ctx){
        ctx.enqueueWork(() -> clientHandler.accept(msg));
    }
}
