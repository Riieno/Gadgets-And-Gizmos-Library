package com.rieno.gadgetsandgizmos.lib.graph;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

// Retain personal runtime values while leaving shared graph state available as defaults
public final class PlayerScopedMap<V> extends AbstractMap<String, V>{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private final Map<String, V> shared = new LinkedHashMap<>();
    private final Map<UUID, Map<String, V>> players = new LinkedHashMap<>(16, 0.75F, true);
    private final Supplier<UUID> player;
    private final Predicate<String> personal;
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public PlayerScopedMap(Supplier<UUID> player, Predicate<String> personal){ this.player = player; this.personal = personal; }

    // Read a player's value before the shared fallback
    @Override public V get(Object key){
        UUID id = player.get();
        Map<String, V> values = id == null ? null : players.get(id);
        return values != null && values.containsKey(key) ? values.get(key) : shared.get(key);
    }
    // Write only personal keys into the current player's scope
    @Override public V put(String key, V val){
        UUID id = player.get();
        if(id == null || !personal.test(key)) return shared.put(key, val);
        Map<String, V> values = players.computeIfAbsent(id, ignored -> new LinkedHashMap<>());
        while(players.size() > 128) players.remove(players.keySet().iterator().next());
        return values.put(key, val);
    }
    @Override public V remove(Object key){
        UUID id = player.get();
        Map<String, V> values = id == null ? null : players.get(id);
        if(key instanceof String name && id != null && personal.test(name)) return values == null ? null : values.remove(key);
        return shared.remove(key);
    }
    @Override public void clear(){ shared.clear(); players.clear(); }
    @Override public Set<Entry<String, V>> entrySet(){
        Map<String, V> values = new LinkedHashMap<>(shared);
        UUID id = player.get();
        if(id != null) values.putAll(players.getOrDefault(id, Map.of()));
        return java.util.Collections.unmodifiableMap(values).entrySet();
    }
    // Export shared state without merging the currently active player's values
    public Map<String, V> sharedSnapshot(){ return Map.copyOf(shared); }
    // Export retained player state separately from the shared runtime snapshot
    public Map<UUID, Map<String, V>> playerSnapshot(){
        Map<UUID, Map<String, V>> res = new LinkedHashMap<>();
        players.forEach((id, values) -> res.put(id, Map.copyOf(values)));
        return Map.copyOf(res);
    }
    // Restore a saved personal scope before its host recompiles the graph
    public void restorePlayer(UUID id, Map<String, V> values){
        players.put(id, new LinkedHashMap<>(values));
        while(players.size() > 128) players.remove(players.keySet().iterator().next());
    }
    // Remove values that no longer belong to the host's current personal scope
    public void prunePlayers(){
        players.values().forEach(values -> values.keySet().removeIf(key -> !personal.test(key)));
        players.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }
}
