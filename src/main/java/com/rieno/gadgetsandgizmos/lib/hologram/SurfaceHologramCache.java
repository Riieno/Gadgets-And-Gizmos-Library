package com.rieno.gadgetsandgizmos.lib.hologram;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

// Share bounded terrain work between projections on the owning server thread
public final class SurfaceHologramCache{
    private final Map<ServerLevel, Cache> levels = new WeakHashMap<>();
    private final int capacity;
    private final int refreshTicks;
    private final int chunksPerTick;

    public SurfaceHologramCache(int capacity, int refreshTicks, int chunksPerTick){
        if(capacity < 1 || refreshTicks < 1 || chunksPerTick < 1) throw new IllegalArgumentException("Invalid cache budget");
        this.capacity = capacity;
        this.refreshTicks = refreshTicks;
        this.chunksPerTick = chunksPerTick;
    }

    // An empty result asks the caller to retry after this tick's shared work budget
    public Optional<HologramChunk> get(ServerLevel level, ChunkPos pos, boolean obfuscateOres){
        return get(level, pos, obfuscateOres, HologramSlice.SURFACE);
    }

    // Slice boundaries are part of the cache key so cutaways cannot reuse surface geometry
    public Optional<HologramChunk> get(ServerLevel level, ChunkPos pos, boolean obfuscateOres, HologramSlice slice){
        Cache cache = levels.computeIfAbsent(level, ignored -> new Cache());
        long now = level.getGameTime();
        if(cache.tick != now){ cache.tick = now; cache.remaining = chunksPerTick; }
        Key key = new Key(pos.toLong(), obfuscateOres, slice);
        Entry entry = cache.entries.get(key);
        if(entry != null && now - entry.tick < refreshTicks) return Optional.of(entry.chunk);
        if(cache.remaining <= 0) return Optional.empty();
        cache.remaining--;
        HologramChunk chunk = SurfaceHologramSampler.sample(level, pos, obfuscateOres, slice);
        cache.entries.put(key, new Entry(now, chunk));
        while(cache.entries.size() > capacity) cache.entries.remove(cache.entries.keySet().iterator().next());
        return Optional.of(chunk);
    }

    public void clear(){ levels.clear(); }

    private record Key(long pos, boolean obfuscateOres, HologramSlice slice){}
    private record Entry(long tick, HologramChunk chunk){}
    private static final class Cache{
        private final Map<Key, Entry> entries = new LinkedHashMap<>(16, 0.75F, true);
        private long tick = Long.MIN_VALUE;
        private int remaining;
    }
}
