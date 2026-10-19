package com.rieno.gadgetsandgizmos.lib.graph;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PlayerScopedMapTest{
    @Test void restoredPersonalValuesCannotMaskKeysThatAreNowShared(){
        UUID player = UUID.randomUUID();
        var active = new AtomicReference<UUID>();
        var personal = new HashSet<String>();
        var values = new PlayerScopedMap<Boolean>(active::get, personal::contains);
        values.put("flip:value", false);
        values.restorePlayer(player, Map.of("flip:value", true));
        active.set(player);
        assertEquals(false, values.get("flip:value"));
        assertEquals(Map.of("flip:value", false), Map.copyOf(values));
        values.put("flip:value", true);
        values.put("flip:value", false);
        assertEquals(false, values.get("flip:value"));
        personal.add("flip:value");
        assertEquals(true, values.get("flip:value"));
        personal.clear();
        values.prunePlayers();
        assertTrue(values.playerSnapshot().isEmpty());
    }

    @Test void personalBooleansStayIndependentAndFallBackToSharedDefaults(){
        var active = new AtomicReference<UUID>();
        var values = new PlayerScopedMap<Boolean>(active::get, key -> true);
        values.put("visible", false);
        UUID first = UUID.randomUUID();
        active.set(first);values.put("visible", true);
        active.set(UUID.randomUUID());
        assertEquals(false, values.get("visible"));
        active.set(first);
        assertEquals(true, values.get("visible"));
        active.set(null);
        assertEquals(false, values.get("visible"));
    }
}
