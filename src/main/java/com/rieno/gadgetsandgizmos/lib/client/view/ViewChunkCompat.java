package com.rieno.gadgetsandgizmos.lib.client.view;

import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

// Track ready Sodium chunks independently without draining the player's event queue
public final class ViewChunkCompat{
    private final Object manager;
    private final Object tracker;
    private final Method readyChunks;
    private final @Nullable Method beforeUpdates;
    private final Method addChunk;
    private final Method removeChunk;
    private final LongSet known = new LongOpenHashSet();

    // Start with the chunks already populated by Sodium's renderer initialization
    public ViewChunkCompat(Object renderer){
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
            known.addAll((LongCollection) readyChunks.invoke(tracker));
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
            var added = ready.iterator();
            while(added.hasNext()){
                long pos = added.nextLong();
                if(inRange(pos, x, z, distance) && known.add(pos)){
                    addChunk.invoke(manager, ChunkPos.getX(pos), ChunkPos.getZ(pos));
                }
            }
        }catch(ReflectiveOperationException err){
            throw new IllegalStateException("Sodium secondary chunks could not be updated", err);
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
