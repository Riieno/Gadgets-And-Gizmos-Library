package com.rieno.gadgetsandgizmos.lib.client.render;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.profile.PlayerSkinLookup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.PlayerSkin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.LongSupplier;

// Keep host-supplied fallback skins visible until UUID profiles and their textures finish loading
public final class PlayerSkinCache{
    private static final int MAX_PROFILES = 128;
    private static final long SUCCESS_DURATION = TimeUnit.MINUTES.toNanos(10);
    private static final long RETRY_DURATION = TimeUnit.MINUTES.toNanos(1);
    private final Map<UUID, Entry> profiles = new LinkedHashMap<>(16, 0.75F, true);
    private final Function<UUID, CompletableFuture<PlayerSkin>> loader;
    private final LongSupplier clock;

    public PlayerSkinCache(){
        this(PlayerSkinCache::load, System::nanoTime);
    }

    PlayerSkinCache(Function<UUID, CompletableFuture<PlayerSkin>> loader, LongSupplier clock){
        this.loader = loader;
        this.clock = clock;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Call on the client thread and preserve the exact fallback on missing, failed or pending skins
    public PlayerSkin skin(UUID id, PlayerSkin fallback){
        if(id == null) return fallback;
        long now = clock.getAsLong();
        Entry entry = profiles.get(id);
        PlayerSkin loaded = entry == null ? null : entry.future().getNow(null);
        if(entry == null || now - entry.started() >= (loaded == null ? RETRY_DURATION : SUCCESS_DURATION)){
            CompletableFuture<PlayerSkin> future;
            try{
                future = loader.apply(id).thenApply(skin -> skin != null && skin.textureUrl() != null ? skin : null)
                        .exceptionally(err -> null).completeOnTimeout(null, 30, TimeUnit.SECONDS);
            }catch(RuntimeException err){
                future = CompletableFuture.completedFuture(null);
            }
            entry = new Entry(future, now);
            profiles.put(id, entry);
            if(profiles.size() > MAX_PROFILES) profiles.remove(profiles.keySet().iterator().next());
            loaded = future.getNow(null);
        }
        return loaded == null ? fallback : loaded;
    }

    // Release this host's profile state while leaving Minecraft's shared texture cache intact
    public void clear(){ profiles.clear(); }

    // Let Minecraft validate signed texture data and finish downloading before exposing a live skin
    private static CompletableFuture<PlayerSkin> load(UUID id){
        return PlayerSkinLookup.lookupUuid(id).thenCompose(res -> {
            if(!res.found() || res.profile() == null) return CompletableFuture.completedFuture(null);
            var mc = Minecraft.getInstance();
            return mc.submit(() -> mc.getSkinManager().getOrLoad(res.profile())).thenCompose(future -> future);
        });
    }

    private record Entry(CompletableFuture<PlayerSkin> future, long started){}
}
