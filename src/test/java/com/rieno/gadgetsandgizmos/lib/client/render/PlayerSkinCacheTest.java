package com.rieno.gadgetsandgizmos.lib.client.render;

import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

// Keep local skins on pending and failed requests while following live skin model changes
class PlayerSkinCacheTest{
    @Test void pendingSkinsKeepEachCallersFallbackUntilTheTextureIsReady(){
        var pending = new CompletableFuture<PlayerSkin>();
        var calls = new AtomicInteger();
        var cache = new PlayerSkinCache(id -> { calls.incrementAndGet(); return pending; }, () -> 0);
        var id = UUID.randomUUID(); var first = skin("first", false, false); var second = skin("second", true, false);
        assertSame(first, cache.skin(id, first));
        assertSame(second, cache.skin(id, second));
        assertEquals(1, calls.get());
        var live = skin("live", true, true); pending.complete(live);
        assertSame(live, cache.skin(id, first));
        assertEquals(PlayerSkin.Model.SLIM, cache.skin(id, first).model());
    }

    @Test void failedAndDefaultSkinsKeepLocalTexturesAndRetryAfterBackoff(){
        var time = new AtomicLong(); var calls = new AtomicInteger();
        var live = skin("live", false, true); var fallback = skin("fallback", true, false);
        var cache = new PlayerSkinCache(id -> calls.incrementAndGet() == 1
                ? CompletableFuture.failedFuture(new IllegalStateException("offline")) : CompletableFuture.completedFuture(live), time::get);
        var id = UUID.randomUUID();
        assertSame(fallback, cache.skin(id, fallback));
        time.set(TimeUnit.SECONDS.toNanos(30));
        assertSame(fallback, cache.skin(id, fallback)); assertEquals(1, calls.get());
        time.set(TimeUnit.MINUTES.toNanos(1));
        assertSame(live, cache.skin(id, fallback)); assertEquals(2, calls.get());
        var defaults = new PlayerSkinCache(uuid -> CompletableFuture.completedFuture(skin("steve", false, false)), () -> 0);
        assertSame(fallback, defaults.skin(id, fallback));
        assertSame(fallback, defaults.skin(null, fallback));
    }

    @Test void expiredProfilesRefreshAndUseLocalFallbackWhenTheServiceGoesOffline(){
        var time = new AtomicLong(); var calls = new AtomicInteger();
        var live = skin("live", true, true); var fallback = skin("fallback", false, false);
        var cache = new PlayerSkinCache(id -> CompletableFuture.completedFuture(calls.incrementAndGet() == 1 ? live : null), time::get);
        var id = UUID.randomUUID();
        assertSame(live, cache.skin(id, fallback));
        time.set(TimeUnit.MINUTES.toNanos(10));
        assertSame(fallback, cache.skin(id, fallback));
        cache.clear(); cache.skin(id, fallback); assertEquals(3, calls.get());
    }

    private static PlayerSkin skin(String name, boolean slim, boolean live){
        return new PlayerSkin(ResourceLocation.fromNamespaceAndPath("test", name), live ? "https://textures.minecraft.net/texture/" + name : null,
                null, null, slim ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE, true);
    }
}
