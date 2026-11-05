package com.rieno.gadgetsandgizmos.lib.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

// Serialize registered payloads into bounded chunks and pace each connection across ticks
public final class PayloadTransferChannel{
    private static final java.util.List<PayloadTransferChannel> CHANNELS = new CopyOnWriteArrayList<>();
    private final CustomPacketPayload.Type<Chunk> type;
    private final Map<ResourceLocation, Route<?>> routes = new ConcurrentHashMap<>();
    // Keep sending and receiving state isolated by the actual connection
    private final Map<Connection, Outgoing> outgoing = new ConcurrentHashMap<>();
    private final Map<Connection, Incoming> incoming = new ConcurrentHashMap<>();

    // Use the consuming mod's namespace for its transfer protocol
    public PayloadTransferChannel(ResourceLocation id){ type = new CustomPacketPayload.Type<>(id); CHANNELS.add(this); }

    // Register the shared chunk envelope alongside the consuming mod's normal packets
    public void register(PayloadRegistrar registrar){
        registrar.playBidirectional(type, StreamCodec.of(this::encodeChunk, this::decodeChunk), this::handle);
    }

    // Preserve normal handlers and their authorization for serverbound transfers
    public <T extends CustomPacketPayload> void playToServer(PayloadRegistrar registrar, CustomPacketPayload.Type<T> id,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, IPayloadHandler<T> handler){
        routes.put(id.id(), new Route<>(codec, handler, PacketFlow.SERVERBOUND));
        registrar.playToServer(id, codec, handler);
    }

    // Keep client payload registration under the consuming mod's ownership
    public <T extends CustomPacketPayload> void playToClient(PayloadRegistrar registrar, CustomPacketPayload.Type<T> id,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, IPayloadHandler<T> handler){
        routes.put(id.id(), new Route<>(codec, handler, PacketFlow.CLIENTBOUND));
        registrar.playToClient(id, codec, handler);
    }

    // Retain small messages on the original protocol and queue larger messages in order
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload){
        send(player.connection.getConnection(), player.registryAccess(), payload, PacketFlow.CLIENTBOUND,
                val -> PacketDistributor.sendToPlayer(player, val), null);
    }

    // Avoid queuing another large baseline while the same target is still transferring
    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload, Object key){
        send(player.connection.getConnection(), player.registryAccess(), payload, PacketFlow.CLIENTBOUND,
                val -> PacketDistributor.sendToPlayer(player, val), key);
    }

    // Test pending baselines before constructing their full NBT snapshots
    boolean isPending(ServerPlayer player, Object key){
        Outgoing queue = outgoing.get(player.connection.getConnection());
        if(queue == null) return false;
        synchronized(queue){ return queue.pending.stream().anyMatch(transfer -> key.equals(transfer.key)); }
    }

    // Supply the active client connection without loading client classes on a server
    public void sendToServer(Connection connection, RegistryAccess registries, CustomPacketPayload payload){
        send(connection, registries, payload, PacketFlow.SERVERBOUND, PacketDistributor::sendToServer, null);
    }

    // Flush two bounded chunks per connection per tick, preserving transfer order
    public static void flush(Connection connection){
        for(var channel : CHANNELS){
            Outgoing queue = channel.outgoing.get(connection);
            if(queue != null){
                synchronized(queue){
                    for(int i = 0; i < 2 && !queue.pending.isEmpty(); i++){
                        Transfer transfer = queue.pending.peek();
                        int end = Math.min(transfer.bytes.length, transfer.offset + PayloadTransferBuffer.CHUNK_BYTES);
                        transfer.sender.accept(new Chunk(channel.type, transfer.route, transfer.id, transfer.bytes.length,
                                transfer.offset, Arrays.copyOfRange(transfer.bytes, transfer.offset, end)));
                        transfer.offset = end;
                        if(end == transfer.bytes.length){ queue.pending.remove(); queue.bytes -= end; }
                    }
                }
            }
            Incoming assembly = channel.incoming.get(connection);
            if(assembly != null && System.nanoTime() - assembly.lastReceive > PayloadTransferBuffer.TIMEOUT_NANOS)
                channel.incoming.remove(connection, assembly);
        }
    }

    // Service only players owned by the server that just ticked
    public static void flush(MinecraftServer server){
        for(ServerPlayer player : server.getPlayerList().getPlayers()) flush(player.connection.getConnection());
        for(var channel : CHANNELS){
            channel.outgoing.keySet().removeIf(connection -> !connection.isConnected());
            channel.incoming.keySet().removeIf(connection -> !connection.isConnected());
        }
    }

    // Release queued bytes when a connection leaves a world
    public static void clear(Connection connection){
        for(var channel : CHANNELS){ channel.outgoing.remove(connection); channel.incoming.remove(connection); }
    }

    // Release server-owned state even when a stopped server will never tick again
    public static void clear(MinecraftServer server){
        for(var channel : CHANNELS){
            var connections = new java.util.HashSet<>(channel.outgoing.keySet());
            connections.addAll(channel.incoming.keySet());
            for(Connection connection : connections){
                if(!connection.isConnected() || connection.getPacketListener() instanceof
                        net.minecraft.server.network.ServerGamePacketListenerImpl listener && listener.player.server == server) clear(connection);
            }
        }
    }

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        HELPERS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Serialize on the owning thread before the mutable source can change
    private void send(Connection connection, RegistryAccess registries, CustomPacketPayload payload,
            PacketFlow flow, Consumer<CustomPacketPayload> sender, Object key){
        Route<?> route = routes.get(payload.type().id());
        if(route == null){ sender.accept(payload); return; }
        if(route.flow != flow) throw new IllegalArgumentException("Invalid payload direction");
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(
                Unpooled.buffer(256, PayloadTransferBuffer.MAX_BYTES), registries);
        byte[] bytes;
        try{ route.encode(buf, payload); bytes = new byte[buf.readableBytes()]; buf.readBytes(bytes); }
        finally{ buf.release(); }
        Outgoing queue = outgoing.computeIfAbsent(connection, ignored -> new Outgoing());
        synchronized(queue){
            if(key != null && queue.pending.stream().anyMatch(transfer -> key.equals(transfer.key))) return;
            if(queue.pending.isEmpty() && bytes.length <= PayloadTransferBuffer.CHUNK_BYTES){ sender.accept(payload); return; }
            if(queue.bytes > PayloadTransferBuffer.MAX_BYTES * 2 - bytes.length)
                throw new IllegalArgumentException("Payload transfer queue is full");
            queue.pending.add(new Transfer(payload.type().id(), bytes, sender, key));
            queue.bytes += bytes.length;
        }
    }

    // Dispatch a complete decoded payload through its original game-thread handler
    private void handle(Chunk chunk, IPayloadContext ctx){
        ctx.enqueueWork(() -> {
            Route<?> route = routes.get(chunk.route);
            if(route == null || route.flow != ctx.flow()) throw new IllegalArgumentException("Unregistered payload transfer");
            Incoming assembly = incoming.computeIfAbsent(ctx.connection(), ignored -> new Incoming(chunk.route));
            if(!assembly.route.equals(chunk.route)) throw new IllegalArgumentException("Overlapping payload routes");
            assembly.lastReceive = System.nanoTime();
            byte[] bytes = assembly.buffer.receive(chunk.id, chunk.total, chunk.offset, chunk.data, assembly.lastReceive);
            if(bytes == null) return;
            incoming.remove(ctx.connection());
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes), ctx.player().registryAccess());
            try{ route.handle(buf, ctx); }
            finally{ buf.release(); }
        });
    }

    // Encode one independently sized slice of the serialized packet
    private void encodeChunk(RegistryFriendlyByteBuf buf, Chunk chunk){
        buf.writeResourceLocation(chunk.route); buf.writeUUID(chunk.id);
        buf.writeVarInt(chunk.total); buf.writeVarInt(chunk.offset); buf.writeByteArray(chunk.data);
    }

    // Bound byte allocation before the packet reaches a game-thread assembly
    private Chunk decodeChunk(RegistryFriendlyByteBuf buf){
        return new Chunk(type, buf.readResourceLocation(), buf.readUUID(), buf.readVarInt(), buf.readVarInt(),
                buf.readByteArray(PayloadTransferBuffer.CHUNK_BYTES));
    }

    // Carry transport metadata without retaining the source packet
    private record Chunk(CustomPacketPayload.Type<Chunk> type, ResourceLocation route, UUID id,
                         int total, int offset, byte[] data) implements CustomPacketPayload{}

    // Retain the typed codec and its authorized direction
    private record Route<T extends CustomPacketPayload>(StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                        IPayloadHandler<T> handler, PacketFlow flow){
        // Encode only payloads registered against this route
        @SuppressWarnings("unchecked")
        private void encode(RegistryFriendlyByteBuf buf, CustomPacketPayload payload){ codec.encode(buf, (T) payload); }
        // Reject trailing data before invoking the original handler
        private void handle(RegistryFriendlyByteBuf buf, IPayloadContext ctx){
            T payload = codec.decode(buf);
            if(buf.isReadable()) throw new IllegalArgumentException("Trailing payload bytes");
            handler.handle(payload, ctx);
        }
    }

    // Own one connection's bounded FIFO
    private static final class Outgoing{
        private final ArrayDeque<Transfer> pending = new ArrayDeque<>();
        private int bytes;
    }

    // Keep the serialized packet until its final slice leaves the queue
    private static final class Transfer{
        private final UUID id = UUID.randomUUID();
        private final ResourceLocation route;
        private final byte[] bytes;
        private final Consumer<CustomPacketPayload> sender;
        private final Object key;
        private int offset;
        // Capture immutable encoded bytes and the destination
        private Transfer(ResourceLocation route, byte[] bytes, Consumer<CustomPacketPayload> sender, Object key){
            this.route = route; this.bytes = bytes; this.sender = sender; this.key = key;
        }
    }

    // Release the only active assembly when its connection or timeout expires
    private static final class Incoming{
        private final ResourceLocation route;
        private final PayloadTransferBuffer buffer = new PayloadTransferBuffer();
        private long lastReceive;
        // Bind the stream to its first route
        private Incoming(ResourceLocation route){ this.route = route; }
    }
}
