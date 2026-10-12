package com.rieno.gadgetsandgizmos.lib.chat;

import java.util.Collection;
import java.util.UUID;

// Match authenticated chat senders by account name or UUID; an empty list accepts everyone
public final class ChatPlayerFilter{
    private ChatPlayerFilter(){}

    public static boolean matches(Collection<String> players, UUID playerId, String playerName){
        if(players == null || players.isEmpty()) return true;
        String id = playerId == null ? "" : playerId.toString();
        String name = playerName == null ? "" : playerName;
        for(String player : players){
            if(player == null || player.isBlank()) continue;
            String entry = player.strip();
            if(!id.isEmpty() && id.equalsIgnoreCase(entry)) return true;
            if(!name.isEmpty() && name.equalsIgnoreCase(entry)) return true;
        }
        return false;
    }
}
