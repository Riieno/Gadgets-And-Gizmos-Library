package com.rieno.gadgetsandgizmos.lib.util;

import net.minecraft.server.MinecraftServer;

import java.util.UUID;

// Resolve player names from online players and the local profile cache
public final class PlayerNames{
    // Keep name resolution behind the static API
    private PlayerNames(){}

    // Fall back to the UUID when no cached profile is available
    public static String resolve(MinecraftServer server, UUID playerId){
        if(playerId == null) return "";
        if(server == null) return playerId.toString();
        var player = server.getPlayerList().getPlayer(playerId);
        if(player != null) return player.getGameProfile().getName();
        var cache = server.getProfileCache();
        return cache == null ? playerId.toString() : cache.get(playerId)
                .map(profile -> profile.getName()).orElse(playerId.toString());
    }
}
