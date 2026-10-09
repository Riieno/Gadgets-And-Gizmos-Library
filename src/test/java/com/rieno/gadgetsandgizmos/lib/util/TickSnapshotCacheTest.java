package com.rieno.gadgetsandgizmos.lib.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TickSnapshotCacheTest{
    @Test
    void inputChangesTicksAndExplicitInvalidationRefreshTheSnapshot(){
        var cache = new TickSnapshotCache<Integer>();
        var calls = new AtomicInteger();
        assertEquals(1, cache.get("first", 10L, calls::incrementAndGet));
        assertEquals(1, cache.get(new String("first"), 10L, calls::incrementAndGet));
        assertEquals(2, cache.get("second", 10L, calls::incrementAndGet));
        assertEquals(3, cache.get("second", 11L, calls::incrementAndGet));
        cache.invalidate();
        assertEquals(4, cache.get("second", 11L, calls::incrementAndGet));
        assertEquals(5, cache.get("second", 1L, calls::incrementAndGet));
    }

    @Test
    void unavailableSnapshotsAreRetainedOnlyForTheirInputAndTick(){
        var cache = new TickSnapshotCache<String>();
        var calls = new AtomicInteger();
        assertNull(cache.get("body", 1L, () -> { calls.incrementAndGet(); return null; }));
        assertNull(cache.get("body", 1L, () -> "loaded"));
        assertEquals(1, calls.get());
        assertEquals("loaded", cache.get("body", 2L, () -> "loaded"));
    }
}
