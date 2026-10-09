package com.rieno.gadgetsandgizmos.lib.network;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Send live block-entity data without scheduling block or chunk mesh updates
public final class BlockEntityDataSync{
    private static final Set<BlockEntity> PENDING = ConcurrentHashMap.newKeySet();

    private BlockEntityDataSync(){}

    // Coalesce physics-thread requests and resolve tracking players on the game thread
    public static void enqueue(BlockEntity component){
        if(component == null || !(component.getLevel() instanceof ServerLevel level) || !PENDING.add(component)) return;
        level.getServer().execute(() -> {
            PENDING.remove(component);
            if(component.isRemoved() || component.getLevel() != level
                    || level.getBlockEntity(component.getBlockPos()) != component) return;
            var packet = component.getUpdatePacket();
            if(packet == null) return;
            for(var player : level.getChunkSource().chunkMap.getPlayers(new ChunkPos(component.getBlockPos()), false)){
                player.connection.send(packet);
            }
        });
    }
}
