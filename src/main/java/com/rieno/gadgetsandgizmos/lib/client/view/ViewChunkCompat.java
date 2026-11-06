package com.rieno.gadgetsandgizmos.lib.client.view;

import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.function.LongConsumer;

// Track ready Sodium chunks independently without draining the player's event queue
public final class ViewChunkCompat{
    // Spread cold terrain allocation across refreshes instead of rebuilding the player window at once
    private static final int MAX_CHUNK_ADDITIONS = 16;
    private final Object manager;
    private final Object tracker;
    private final Method readyChunks;
    private final @Nullable Method beforeUpdates;
    private final Method addChunk;
    private final Method removeChunk;
    private final LongSet known = new LongOpenHashSet();

    // Start with the chunks already populated by Sodium's renderer initialization
    public ViewChunkCompat(Object renderer){
        this(renderer, null);
    }
    // Retain the actual initialization window while older Sodium variants keep their original chunk history
    public ViewChunkCompat(Object renderer, @Nullable LongCollection initial){
        try{
            var managerField = renderer.getClass().getDeclaredField("renderSectionManager");
            var levelField = renderer.getClass().getDeclaredField("level");
            managerField.setAccessible(true);
            levelField.setAccessible(true);
            manager = managerField.get(renderer);
            Class<?> holder = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder",
                    false, renderer.getClass().getClassLoader());
            tracker = holder.getMethod("get", ClientLevel.class).invoke(null, levelField.get(renderer));
            readyChunks = tracker.getClass().getMethod("getReadyChunks");
            beforeUpdates = beforeUpdates(manager);
            addChunk = manager.getClass().getMethod("onChunkAdded", int.class, int.class);
            removeChunk = manager.getClass().getMethod("onChunkRemoved", int.class, int.class);
            known.addAll(initial == null ? (LongCollection)readyChunks.invoke(tracker) : initial);
        }catch(ReflectiveOperationException err){
            throw new IllegalStateException("Sodium secondary chunk tracking is unavailable", err);
        }
    }
    // Apply loaded and unloaded chunks only to this source's terrain manager
    public void update(){
        try{
            if(beforeUpdates != null) beforeUpdates.invoke(manager);
            LongCollection ready = (LongCollection) readyChunks.invoke(tracker);
            var lens = ViewSceneRenderer.capturePose();
            if(lens == null) return;
            int x = SectionPos.blockToSectionCoord(lens.position().x);
            int z = SectionPos.blockToSectionCoord(lens.position().z);
            int distance = (int) (ViewSceneRenderer.captureDistance() / 16) + 1;
            var removed = known.iterator();
            while(removed.hasNext()){
                long pos = removed.nextLong();
                if(ready.contains(pos) && inRange(pos, x, z, distance)) continue;
                removeChunk.invoke(manager, ChunkPos.getX(pos), ChunkPos.getZ(pos));
                removed.remove();
            }
            int[] remaining = {MAX_CHUNK_ADDITIONS};
            forEachReadyChunk(ready, x, z, distance, pos -> {
                if(remaining[0] > 0 && known.add(pos)){
                    remaining[0]--;
                    try{
                        addChunk.invoke(manager, ChunkPos.getX(pos), ChunkPos.getZ(pos));
                    }catch(ReflectiveOperationException err){
                        throw new IllegalStateException("Sodium secondary chunk could not be added", err);
                    }
                }
            });
        }catch(ReflectiveOperationException err){
            throw new IllegalStateException("Sodium secondary chunks could not be updated", err);
        }
    }
    // Seed only nearby terrain while a secondary renderer initializes
    public static LongCollection initialChunks(LongCollection ready){
        var lens = ViewSceneRenderer.capturePose();
        if(lens == null) return ready;
        int x = SectionPos.blockToSectionCoord(lens.position().x);
        int z = SectionPos.blockToSectionCoord(lens.position().z);
        int distance = (int)(ViewSceneRenderer.captureDistance() / 16) + 1;
        return initialChunks(ready, x, z, distance);
    }
    // Collect the nearest initial chunks without allocating the entire loaded player window
    static LongCollection initialChunks(LongCollection ready, int x, int z, int distance){
        LongSet chunks = new LongOpenHashSet();
        forEachReadyChunk(ready, x, z, distance, pos -> {
            if(chunks.size() < MAX_CHUNK_ADDITIONS) chunks.add(pos);
        });
        return chunks;
    }
    // Probe only the lens window instead of walking every chunk loaded around the player
    static void forEachReadyChunk(LongCollection ready, int x, int z, int distance, LongConsumer action){
        for(int ring = 0; ring <= distance; ring++){
            for(int dx = -ring; dx <= ring; dx++){
                for(int dz = -ring; dz <= ring; dz++){
                    if(Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    long pos = ChunkPos.asLong(x + dx, z + dz);
                    if(ready.contains(pos)) action.accept(pos);
                }
            }
        }
    }
    // Older Sodium releases do not need an explicit pre-update stage
    private static @Nullable Method beforeUpdates(Object manager){
        try{
            return manager.getClass().getMethod("beforeSectionUpdates");
        }catch(NoSuchMethodException err){
            return null;
        }
    }
    // Retain a boundary chunk beyond the lens distance for terrain clipping and movement
    private static boolean inRange(long pos, int x, int z, int distance){
        return Math.abs(ChunkPos.getX(pos) - x) <= distance && Math.abs(ChunkPos.getZ(pos) - z) <= distance;
    }
}
