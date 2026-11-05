package com.rieno.gadgetsandgizmos.lib.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

// Fetch large client-only block data after its small vanilla chunk tag has arrived
public final class BlockEntitySnapshotChannel{
    private static final String DEFERRED = "DeferredClientSnapshot";
    public static final int INLINE_BYTES = 32 * 1024;
    private final PayloadTransferChannel transport;
    private final CustomPacketPayload.Type<Request> requestType;
    private final CustomPacketPayload.Type<Snapshot> snapshotType;
    private final BiFunction<ServerPlayer, MenuConfigTarget, BlockEntity> resolver;
    // Retain only bounded request cooldowns while each connection remains alive
    private final Map<Connection, Map<MenuConfigTarget, Long>> requests = Collections.synchronizedMap(new WeakHashMap<>());
    private Consumer<CustomPacketPayload> clientSender = payload -> {};
    private BiConsumer<MenuConfigTarget, CompoundTag> clientHandler = (target, data) -> {};

    // Let the consumer validate exact loaded targets and own both packet identifiers
    public BlockEntitySnapshotChannel(PayloadTransferChannel transport, ResourceLocation id,
            BiFunction<ServerPlayer, MenuConfigTarget, BlockEntity> resolver){
        this.transport = transport;
        this.resolver = resolver;
        requestType = new CustomPacketPayload.Type<>(id.withPath(id.getPath() + "_request"));
        snapshotType = new CustomPacketPayload.Type<>(id);
    }

    // Register typed requests and compressed snapshots through the paced transport
    public void register(PayloadRegistrar registrar){
        transport.playToServer(registrar, requestType, StreamCodec.of(
                (buf, val) -> {
                    buf.writeResourceLocation(val.dimension);
                    MenuConfigTarget.STREAM_CODEC.encode(buf, val.target);
                }, buf -> new Request(requestType, buf.readResourceLocation(), MenuConfigTarget.STREAM_CODEC.decode(buf))),
                (req, ctx) -> ctx.enqueueWork(() -> {
            if(!(ctx.player() instanceof ServerPlayer player)) return;
            if(!player.level().dimension().location().equals(req.dimension)) return;
            if(transport.isPending(player, req)) return;
            long now = player.serverLevel().getGameTime();
            Map<MenuConfigTarget, Long> seen = requests.computeIfAbsent(ctx.connection(), ignored -> new LinkedHashMap<>());
            Long prev = seen.get(req.target);
            if(prev != null && now >= prev && now - prev < 10) return;
            if(seen.size() >= 128) seen.remove(seen.keySet().iterator().next());
            seen.put(req.target, now);
            BlockEntity entity = resolver.apply(player, req.target);
            if(entity instanceof Source source){
                transport.sendToPlayer(player, new Snapshot(snapshotType, req.dimension, req.target,
                        source.clientSnapshot(player.registryAccess())), req);
            }
        }));
        transport.playToClient(registrar, snapshotType, StreamCodec.of(
                (buf, val) -> {
                    buf.writeResourceLocation(val.dimension);
                    MenuConfigTarget.STREAM_CODEC.encode(buf, val.target);
                    CompressedTagCodec.write(buf, val.data);
                }, buf -> new Snapshot(snapshotType, buf.readResourceLocation(), MenuConfigTarget.STREAM_CODEC.decode(buf), CompressedTagCodec.read(buf))),
                (snapshot, ctx) -> ctx.enqueueWork(() -> {
                    if(ctx.player().level().dimension().location().equals(snapshot.dimension)) clientHandler.accept(snapshot.target, snapshot.data);
                }));
    }

    // Install side-safe sending and application callbacks from the consumer's client setup
    public void installClient(Consumer<CustomPacketPayload> sender, BiConsumer<MenuConfigTarget, CompoundTag> handler){
        clientSender = sender; clientHandler = handler;
    }

    // Mark a small metadata tag whose bulk data will arrive separately
    public static void defer(CompoundTag tag){ tag.putBoolean(DEFERRED, true); }

    // Identify deferred chunk tags without deserializing the missing data
    public static boolean isDeferred(CompoundTag tag){ return tag.getBoolean(DEFERRED); }

    // Request a fresh full snapshot after the client has created the target block entity
    public void request(MenuConfigTarget target, ResourceLocation dimension){ clientSender.accept(new Request(requestType, dimension, target)); }

    // Supply client presentation data without exposing a content-specific implementation
    public interface Source{
        // Build a full client baseline on the owning server thread
        CompoundTag clientSnapshot(HolderLookup.Provider provider);
    }

    // Address only a loaded target approved by the consuming mod
    private record Request(CustomPacketPayload.Type<Request> type, ResourceLocation dimension, MenuConfigTarget target) implements CustomPacketPayload{}
    // Carry a complete baseline independently of vanilla chunk NBT limits
    private record Snapshot(CustomPacketPayload.Type<Snapshot> type, ResourceLocation dimension, MenuConfigTarget target, CompoundTag data) implements CustomPacketPayload{}
}
