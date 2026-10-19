package com.rieno.gadgetsandgizmos.lib.chat;

import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

// Route player chat to loaded hosts and deliver literal messages to a selected audience
public final class ServerChatApi{
    private static final Map<MinecraftServer, List<Listener>> LISTENERS = new WeakHashMap<>();

    private ServerChatApi(){}

    // Subscribe until the host unloads or this server stops
    public static Subscription listen(MinecraftServer server, Consumer<Message> receiver){
        Objects.requireNonNull(receiver);
        return intercept(server, message -> {
            receiver.accept(message);
            return false;
        });
    }

    // Return true only when this receiver captures a message that should be hidden from chat
    public static synchronized Subscription intercept(MinecraftServer server, Predicate<Message> receiver){
        Listener listener = new Listener(Objects.requireNonNull(server), Objects.requireNonNull(receiver));
        LISTENERS.computeIfAbsent(server, ignored -> new ArrayList<>()).add(listener);
        return listener;
    }

    // Deliver received player chat on the owning server thread
    public static void publishPlayerChat(MinecraftServer server, String text, UUID playerId){
        if(server == null || text == null) return;
        if(!server.isSameThread()){
            server.execute(() -> publishPlayerChat(server, text, playerId));
            return;
        }
        var player = playerId == null ? null : server.getPlayerList().getPlayer(playerId);
        receivePlayerChat(server, new Message(text, playerId,
                player == null ? "" : player.getGameProfile().getName()));
    }

    // Complete capture on the owning thread before the chat event decides whether to broadcast
    public static boolean receivePlayerChat(MinecraftServer server, Message message){
        if(server == null || message == null) return false;
        if(!server.isSameThread()) return server.submit(() -> receivePlayerChat(server, message)).join();
        List<Listener> listeners;
        synchronized(ServerChatApi.class){
            listeners = List.copyOf(LISTENERS.getOrDefault(server, List.of()));
        }
        boolean hidden = false;
        for(Listener listener : listeners){
            if(!listener.closed && listener.receiver.test(message)) hidden = true;
        }
        return hidden;
    }

    // Send on the server thread; blocked broadcasts never fall back to another audience
    public static boolean send(MinecraftServer server, UUID playerId, String text, boolean global, boolean allowGlobal){
        if(server == null || text == null || text.isBlank()) return false;
        if(global){
            if(!allowGlobal) return false;
            server.getPlayerList().broadcastSystemMessage(Component.literal(text), false);
            return true;
        }
        if(playerId == null) return false;
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if(player == null) return false;
        player.sendSystemMessage(Component.literal(text));
        return true;
    }

    // Send on the server thread to listed online account names or UUIDs, once per recipient
    public static boolean sendToPlayers(MinecraftServer server, Collection<String> players, String text){
        if(server == null || players == null || players.isEmpty() || text == null || text.isBlank()) return false;
        Component message = Component.literal(text);
        boolean delivered = false;
        for(ServerPlayer player : server.getPlayerList().getPlayers()){
            if(!ChatPlayerFilter.matches(players, player.getUUID(), player.getGameProfile().getName())) continue;
            player.sendSystemMessage(message);
            delivered = true;
        }
        return delivered;
    }

    // Send only to this source's sublevel occupants or its centered three-by-three world chunks
    public static boolean sendNearby(BlockEntity source, String text, boolean allowed){
        if(!allowed || source == null || source.isRemoved() || text == null || text.isBlank()) return false;
        BlockEntity host = com.rieno.gadgetsandgizmos.lib.physics.HostedBlockEntities.host(source);
        if(host != null) source = host;
        if(source.getLevel() == null || source.getLevel().isClientSide) return false;
        var level = SableLevelApi.serverLevel(source.getLevel());
        if(level == null || level.getServer() == null) return false;
        UUID subLevelId = SableLevelApi.containingId(source);
        ChunkPos center = new ChunkPos(source.getBlockPos());
        NearbyChatRange range = new NearbyChatRange(subLevelId, center.x, center.z);
        Component message = Component.literal(text);
        boolean delivered = false;
        for(ServerPlayer player : level.getServer().getPlayerList().getPlayers()){
            if(SableLevelApi.serverLevel(player.level()) != level) continue;
            UUID trackedId = null;
            UUID containingId = null;
            ChunkPos playerChunk = center;
            if(subLevelId != null){
                var tracked = SableLevelApi.tracking(player);
                var containing = SableLevelApi.containing(player);
                trackedId = tracked == null ? null : tracked.getUniqueId();
                containingId = containing == null ? null : containing.getUniqueId();
            }else{
                var position = SableTransformApi.projectOut(player.level(), player.position());
                if(position == null) continue;
                playerChunk = new ChunkPos(BlockPos.containing(position));
            }
            if(!range.contains(true, trackedId, containingId, playerChunk.x, playerChunk.z)) continue;
            player.sendSystemMessage(message);
            delivered = true;
        }
        return delivered;
    }

    // Release listeners and host references when one server stops
    public static synchronized void clear(MinecraftServer server){
        List<Listener> listeners = LISTENERS.remove(server);
        if(listeners != null) listeners.forEach(listener -> listener.closed = true);
    }

    public record Message(String text, UUID playerId, String playerName){
        public Message{
            Objects.requireNonNull(text);
            playerName = playerName == null ? "" : playerName;
        }
        public Message(String text, UUID playerId){ this(text, playerId, ""); }
    }

    public interface Subscription extends AutoCloseable{
        @Override void close();
    }

    private static final class Listener implements Subscription{
        private final MinecraftServer server;
        private final Predicate<Message> receiver;
        private volatile boolean closed;

        private Listener(MinecraftServer server, Predicate<Message> receiver){
            this.server = server;
            this.receiver = receiver;
        }

        @Override
        public void close(){
            synchronized(ServerChatApi.class){
                if(closed) return;
                closed = true;
                List<Listener> listeners = LISTENERS.get(server);
                if(listeners == null) return;
                listeners.remove(this);
                if(listeners.isEmpty()) LISTENERS.remove(server);
            }
        }
    }
}

