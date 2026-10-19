package com.rieno.gadgetsandgizmos.lib.control;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Prepare immutable action groups for repeated control passes on the owning thread
public final class ActionGroupRouting<T>{
    private static final int CACHE_LIMIT = 64;
    private final Map<String, String> bindings;
    private final Map<String, Set<T>> firstGroups = new HashMap<>();
    private final Map<String, Set<T>> groups = new HashMap<>();
    private final Map<Set<String>, Set<T>> selections = new LinkedHashMap<>(16, 0.75F, true);

    public ActionGroupRouting(Collection<Group<T>> values, Map<String, String> bindings){
        this.bindings = Map.copyOf(bindings);
        Map<String, Set<T>> collected = new LinkedHashMap<>();
        for(Group<T> group : values){
            firstGroups.putIfAbsent(group.id(), Set.copyOf(group.units()));
            collected.computeIfAbsent(group.id(), ignored -> new LinkedHashSet<>()).addAll(group.units());
        }
        collected.forEach((id, units) -> groups.put(id, Set.copyOf(units)));
    }

    // Resolve only the first group with the bound id
    public Set<T> firstUnitsForAction(String action){
        String id = action == null ? null : bindings.get(action);
        return id == null || id.isBlank() ? Set.of() : firstGroups.getOrDefault(id, Set.of());
    }

    // Require every requested action to name an existing group
    public boolean hasGroupsFor(Collection<String> actions){
        if(actions == null || actions.isEmpty()) return false;
        for(String action : actions){
            String id = action == null ? null : bindings.get(action);
            if(id == null || !groups.containsKey(id)) return false;
        }
        return true;
    }

    public boolean hasBindings(){
        return bindings.values().stream().anyMatch(id -> !id.isBlank() && groups.containsKey(id));
    }

    // Select all matching groups only when every action is bound
    public Set<T> unitsForActions(Collection<String> actions){
        if(!hasGroupsFor(actions)) return Set.of();
        return select(actions, null);
    }

    // Include the implicit group with any nonempty explicit request
    public Set<T> unitsForExplicitActions(Collection<String> actions, String implicitAction){
        if(actions == null || actions.isEmpty() || bindings.isEmpty()) return Set.of();
        return select(actions, implicitAction);
    }

    private Set<T> select(Collection<String> actions, String implicitAction){
        Set<String> ids = new LinkedHashSet<>();
        if(implicitAction != null) addGroup(ids, implicitAction);
        for(String action : actions) addGroup(ids, action);
        Set<String> key = Set.copyOf(ids);
        Set<T> cached = selections.get(key);
        if(cached != null) return cached;
        Set<T> units = new LinkedHashSet<>();
        for(String id : ids) units.addAll(groups.getOrDefault(id, Set.of()));
        Set<T> res = Set.copyOf(units);
        if(selections.size() >= CACHE_LIMIT) selections.remove(selections.keySet().iterator().next());
        selections.put(key, res);
        return res;
    }

    private void addGroup(Set<String> ids, String action){
        String id = action == null ? null : bindings.get(action);
        if(id != null && !id.isBlank()) ids.add(id);
    }

    public record Group<T>(String id, List<T> units){
        public Group{ units = List.copyOf(units); }
    }
}
