package com.rieno.gadgetsandgizmos.lib.schematic;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.logging.LogUtils;
import com.rieno.gadgetsandgizmos.lib.client.schematic.ClientSchematicSnapshots;
import com.rieno.gadgetsandgizmos.lib.nbt.ChunkedTagPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

// Transfer approved schematic snapshots without sending whole graphs in one packet
@EventBusSubscriber(modid = "gadgetsngizmos", bus = EventBusSubscriber.Bus.MOD)
public final class SchematicSnapshotPackets{
    public static final int MAX_BLOCKS = 512;
    public static final int MAX_TRANSFER_BYTES = 64 * 1024 * 1024;
    private static final Map<ServerPlayer, Long> LAST_REQUEST = new WeakHashMap<>();

    private SchematicSnapshotPackets(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Register requests and bounded snapshot segments under the library namespace
    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent evt){
        var registrar = evt.registrar("1");
        registrar.playToServer(Request.TYPE, Request.STREAM_CODEC, (msg, ctx) -> ctx.enqueueWork(() -> {
            if(ctx.player() instanceof ServerPlayer player) send(player, msg);
        }));
        registrar.playToClient(Segment.TYPE, Segment.STREAM_CODEC, (msg, ctx) ->
                ctx.enqueueWork(() -> ClientSchematicSnapshots.accept(msg)));
    }

    // Capture loaded blocks near the requesting player without loading additional chunks
    public static Map<BlockPos, CompoundTag> capture(ServerPlayer player, List<BlockPos> positions){
        if(positions.isEmpty() || positions.size() > MAX_BLOCKS)
            throw new IllegalArgumentException("Invalid schematic snapshot selection");
        var level = player.serverLevel();
        int range = player.getServer().getPlayerList().getViewDistance() + 2;
        Map<BlockPos, CompoundTag> res = new LinkedHashMap<>();
        int bytes = 0;
        for(BlockPos pos : positions){
            if(Math.abs((long) (pos.getX() >> 4) - player.chunkPosition().x) > range
                    || Math.abs((long) (pos.getZ() >> 4) - player.chunkPosition().z) > range
                    || !level.hasChunkAt(pos)) throw new IllegalArgumentException("Schematic block is unavailable");
            var blockEntity = level.getBlockEntity(pos);
            if(!(blockEntity instanceof SchematicSnapshotProvider provider))
                throw new IllegalArgumentException("Schematic block has changed");
            CompoundTag envelope = ChunkedTagPayload.encode(provider.schematicSnapshot(level.registryAccess()));
            bytes += envelope.getInt("CompressedBytes");
            if(bytes > MAX_TRANSFER_BYTES) throw new IllegalArgumentException("Schematic snapshots exceed the transfer limit");
            res.put(pos.immutable(), envelope);
        }
        return res;
    }

    // Complete each request explicitly so failed captures cannot produce an incomplete schematic
    private static void send(ServerPlayer player, Request request){
        long time = player.serverLevel().getGameTime();
        Long prev = LAST_REQUEST.put(player, time);
        boolean success = false;
        try{
            if(prev != null && time - prev < 20) throw new IllegalArgumentException("Schematic request is already pending");
            for(var entry : capture(player, request.positions()).entrySet()){
                CompoundTag envelope = entry.getValue();
                var chunks = envelope.getList("Chunks", Tag.TAG_COMPOUND);
                for(int idx = 0; idx < chunks.size(); idx++){
                    PacketDistributor.sendToPlayer(player, new Segment(request.id(), entry.getKey(), idx,
                            envelope.getInt("CompressedBytes"), chunks.getCompound(idx).getByteArray("Data"), true));
                }
            }
            success = true;
        }catch(RuntimeException err){ LogUtils.getLogger().warn("Could not capture schematic block data", err); }
        PacketDistributor.sendToPlayer(player, new Segment(request.id(), BlockPos.ZERO, -1, 0, new byte[0], success));
    }

    // Request only the blocks which opted into authoritative schematic snapshots
    public record Request(UUID id, List<BlockPos> positions) implements CustomPacketPayload{
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("gadgetsngizmos", "schematic_snapshot_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> STREAM_CODEC = StreamCodec.of(Request::encode, Request::decode);
        public Request{ positions = positions.stream().map(BlockPos::immutable).toList(); }
        @Override public Type<? extends CustomPacketPayload> type(){ return TYPE; }
        private static void encode(RegistryFriendlyByteBuf buf, Request msg){
            buf.writeUUID(msg.id());
            buf.writeVarInt(msg.positions().size());
            for(BlockPos pos : msg.positions()) buf.writeBlockPos(pos);
        }
        private static Request decode(RegistryFriendlyByteBuf buf){
            UUID id = buf.readUUID();
            int size = buf.readVarInt();
            if(size <= 0 || size > MAX_BLOCKS) throw new IllegalArgumentException("Invalid schematic snapshot count");
            List<BlockPos> positions = new ArrayList<>(size);
            for(int idx = 0; idx < size; idx++) positions.add(buf.readBlockPos());
            return new Request(id, positions);
        }
    }

    // The final empty segment records success after all block segments have been sent
    public record Segment(UUID id, BlockPos pos, int idx, int size, byte[] bytes, boolean success) implements CustomPacketPayload{
        public static final Type<Segment> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("gadgetsngizmos", "schematic_snapshot_segment"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Segment> STREAM_CODEC = StreamCodec.of(Segment::encode, Segment::decode);
        @Override public Type<? extends CustomPacketPayload> type(){ return TYPE; }
        private static void encode(RegistryFriendlyByteBuf buf, Segment msg){
            buf.writeUUID(msg.id());
            buf.writeBlockPos(msg.pos());
            buf.writeInt(msg.idx());
            buf.writeInt(msg.size());
            buf.writeByteArray(msg.bytes());
            buf.writeBoolean(msg.success());
        }
        private static Segment decode(RegistryFriendlyByteBuf buf){
            return new Segment(buf.readUUID(), buf.readBlockPos(), buf.readInt(), buf.readInt(),
                    buf.readByteArray(ChunkedTagPayload.CHUNK_BYTES), buf.readBoolean());
        }
    }
}
