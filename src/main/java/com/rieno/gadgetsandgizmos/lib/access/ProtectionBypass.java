package com.rieno.gadgetsandgizmos.lib.access;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

// Keep operator protection bypasses within the current player session
public final class ProtectionBypass{
    public static final int REQUIRED_PERMISSION_LEVEL = 2;
    private static final Set<ServerPlayer> ENABLED = Collections.newSetFromMap(new WeakHashMap<>());

    private ProtectionBypass(){}

    // Recheck operator permissions whenever an action consults the bypass
    public static synchronized boolean isEnabled(ServerPlayer player){
        if(player == null || !ENABLED.contains(player)) return false;
        if(player.server != null && player.hasPermissions(REQUIRED_PERMISSION_LEVEL)) return true;
        ENABLED.remove(player);
        return false;
    }

    // Resolve UUID-based permissions against the current online player
    public static synchronized boolean isEnabled(MinecraftServer server, UUID playerId){
        if(server == null || playerId == null || ENABLED.isEmpty()) return false;
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        return player != null && player.server == server && isEnabled(player);
    }

    // Return the resulting state without persisting it in player or world data
    public static synchronized boolean setEnabled(ServerPlayer player, boolean enabled){
        if(player == null) return false;
        if(!enabled || player.server == null || !player.hasPermissions(REQUIRED_PERMISSION_LEVEL)){
            ENABLED.remove(player);
            return false;
        }
        ENABLED.add(player);
        return true;
    }

    public static synchronized void clear(ServerPlayer player){
        ENABLED.remove(player);
    }

    public static synchronized void clear(MinecraftServer server){
        ENABLED.removeIf(player -> player.server == server);
    }
}
