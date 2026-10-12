package com.rieno.gadgetsandgizmos.lib.chat;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

// Preserve saved identities while selecting the players currently online
public final class OnlinePlayerSelection{
    private final LinkedHashSet<String> selected = new LinkedHashSet<>();

    public OnlinePlayerSelection(Collection<String> initial){
        if(initial != null) initial.forEach(id -> { if(id != null) selected.add(id); });
    }

    public boolean contains(Player player){
        return player != null && !selected.isEmpty() && ChatPlayerFilter.matches(selected, player.id(), player.name());
    }

    public void toggle(Player player){
        if(player == null) return;
        if(contains(player)) selected.removeIf(id -> ChatPlayerFilter.matches(List.of(id), player.id(), player.name()));
        else selected.add(player.id().toString());
    }

    public void selectAll(Collection<Player> online){
        if(online != null) for(Player player : online) if(!contains(player)) selected.add(player.id().toString());
    }

    public void clear(){ selected.clear(); }
    public List<String> identifiers(){ return List.copyOf(selected); }

    public long offlineCount(Collection<Player> online){
        return selected.stream().filter(id -> online == null || online.stream()
                .noneMatch(player -> ChatPlayerFilter.matches(List.of(id), player.id(), player.name()))).count();
    }

    public record Player(UUID id, String name){
        public Player{
            Objects.requireNonNull(id);
            name = name == null ? "" : name;
        }
    }
}
