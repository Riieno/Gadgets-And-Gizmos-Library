package com.rieno.gadgetsandgizmos.lib.client.view;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

// Bound initial terrain work while retaining nearby loaded chunks and complete later traversal
class ViewChunkCompatTest{
    // A cold feed must not allocate the player's full loaded terrain window
    @Test void initializationStartsWithSixteenNearbyChunks(){
        var ready = new LongOpenHashSet();
        for(int x = -16; x <= 16; x++) for(int z = -16; z <= 16; z++) ready.add(ChunkPos.asLong(x, z));
        var initial = ViewChunkCompat.initialChunks(ready, 0, 0, 7);
        assertEquals(16, initial.size());
        for(long pos : initial) assertTrue(Math.abs(ChunkPos.getX(pos)) <= 2 && Math.abs(ChunkPos.getZ(pos)) <= 2);
        assertEquals(1089, ready.size());
    }

    // Build the center first and visit each loaded in-range chunk once
    @Test void traversalIsNearestFirstAndComplete(){
        var ready = new LongOpenHashSet();
        for(int x = 4; x <= 10; x++) for(int z = -5; z <= 1; z++) ready.add(ChunkPos.asLong(x, z));
        var ordered = new ArrayList<Long>();
        ViewChunkCompat.forEachReadyChunk(ready, 7, -2, 2, ordered::add);
        assertEquals(25, ordered.size());
        assertEquals(25, new LongOpenHashSet(ordered).size());
        assertEquals(ChunkPos.asLong(7, -2), ordered.getFirst());
    }
}
