package com.rieno.gadgetsandgizmos.lib.graph;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

// Keep personal toggles independent while preserving shared mechanical state
class PlayerScopedMapTest{
    @Test void playersSeeTheirOwnValuesAndSharedDefaults(){
        AtomicReference<UUID> player = new AtomicReference<>();
        PlayerScopedMap<Integer> values = new PlayerScopedMap<>(player::get, key -> key.startsWith("hud:"));
        values.put("hud:visible", 0);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        player.set(first);
        values.put("hud:visible", 1);
        values.put("engine", 15);
        player.set(second);
        assertEquals(0, values.get("hud:visible"));
        assertEquals(15, values.get("engine"));
        values.put("hud:visible", 2);
        player.set(first);
        assertEquals(1, values.get("hud:visible"));
        player.set(null);
        assertEquals(0, values.get("hud:visible"));
        assertEquals(2, values.playerSnapshot().size());
    }
    @Test void restoredAndRemovedScopesStayBounded(){
        AtomicReference<UUID> player = new AtomicReference<>();
        PlayerScopedMap<Integer> values = new PlayerScopedMap<>(player::get, key -> key.equals("hud"));
        for(int idx = 0; idx < 200; idx++) values.restorePlayer(UUID.randomUUID(), java.util.Map.of("hud", idx, "removed", 1));
        assertEquals(128, values.playerSnapshot().size());
        values.prunePlayers();
        assertTrue(values.playerSnapshot().values().stream().allMatch(map -> map.size() == 1));
        values.clear();
        assertTrue(values.playerSnapshot().isEmpty());
    }
}
