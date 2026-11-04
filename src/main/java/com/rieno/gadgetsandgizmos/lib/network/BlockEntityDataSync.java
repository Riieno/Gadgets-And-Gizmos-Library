package com.rieno.gadgetsandgizmos.lib.network;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

// Send live block-entity data without scheduling block or chunk mesh updates
public final class BlockEntityDataSync{
    private static final Map<BlockEntity, ServerLevel> PENDING = new ConcurrentHashMap<>();

    private BlockEntityDataSync(){}

    // Retain the newest state until the owning server finishes its current tick
    public static void enqueue(BlockEntity component){
        if(component == null || !(component.getLevel() instanceof ServerLevel level)) return;
        PENDING.put(component, level);
    }

    // Send each component once on the owning thread after this tick's updates
    public static void flush(MinecraftServer server){
        if(!server.isSameThread()) throw new IllegalStateException("Block entity updates must be flushed on the server thread");
        for(var pending : List.copyOf(PENDING.entrySet())){
            BlockEntity component = pending.getKey();
            ServerLevel level = pending.getValue();
            if(level.getServer() != server || !PENDING.remove(component, level)) continue;
            if(component.isRemoved() || component.getLevel() != level
                    || level.getBlockEntity(component.getBlockPos()) != component) continue;
            var players = level.getChunkSource().chunkMap.getPlayers(new ChunkPos(component.getBlockPos()), false);
            if(players.isEmpty()) continue;
            var packet = component.getUpdatePacket();
            if(packet == null) continue;
            for(var player : players){
                player.connection.send(packet);
            }
        }
    }

    // Release queued components when their server stops
    public static void clear(MinecraftServer server){
        PENDING.entrySet().removeIf(entry -> entry.getValue().getServer() == server);
    }
}
