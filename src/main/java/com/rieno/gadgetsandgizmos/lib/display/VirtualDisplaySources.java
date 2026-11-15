package com.rieno.gadgetsandgizmos.lib.display;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

// Resolve non-block display targets inside their owning server and release them with their source
public final class VirtualDisplaySources{
    public static final String TARGET = "VirtualDisplaySource";
    private static final Map<MinecraftServer, Map<UUID, Source>> SOURCES = new WeakHashMap<>();
    private static final Map<MinecraftServer, Map<UUID, Input>> INPUTS = new WeakHashMap<>();
    private static final Map<MinecraftServer, Map<UUID, java.util.function.Predicate<net.minecraft.server.level.ServerPlayer>>> ACCESS = new WeakHashMap<>();
    private VirtualDisplaySources(){}
    @FunctionalInterface public interface Source{ CompoundTag frame(int width, int height); }
    @FunctionalInterface public interface Input{ boolean accept(net.minecraft.server.level.ServerPlayer player, com.rieno.gadgetsandgizmos.lib.script.ScriptInput input); }

    public static synchronized AutoCloseable register(MinecraftServer server, UUID id, Source source){
        return register(server, id, source, null);
    }
    public static synchronized AutoCloseable register(MinecraftServer server, UUID id, Source source, Input input){
        return register(server, id, source, input, player -> true);
    }
    public static synchronized AutoCloseable register(MinecraftServer server, UUID id, Source source, Input input, java.util.function.Predicate<net.minecraft.server.level.ServerPlayer> access){
        Map<UUID, Source> sources = SOURCES.computeIfAbsent(server, ignored -> new HashMap<>());
        if(sources.putIfAbsent(id, source) != null) throw new IllegalArgumentException("Display source UUID already registered");
        if(input != null) INPUTS.computeIfAbsent(server, ignored -> new HashMap<>()).put(id, input);
        ACCESS.computeIfAbsent(server, ignored -> new HashMap<>()).put(id, access);
        return () -> { synchronized(VirtualDisplaySources.class){
            var current = SOURCES.get(server);
            if(current != null && current.remove(id, source)){
                if(current.isEmpty()) SOURCES.remove(server);
                var inputs = INPUTS.get(server); if(inputs != null){ inputs.remove(id); if(inputs.isEmpty()) INPUTS.remove(server); }
                var permissions = ACCESS.get(server); if(permissions != null){ permissions.remove(id); if(permissions.isEmpty()) ACCESS.remove(server); }
            }
        }};
    }
    public static CompoundTag reference(UUID id){ var tag = new CompoundTag(); tag.putUUID(TARGET, id); return tag; }
    public static boolean isReference(CompoundTag target){ return target != null && target.hasUUID(TARGET); }
    public static CompoundTag frame(MinecraftServer server, CompoundTag target, int width, int height){
        Source source;
        synchronized(VirtualDisplaySources.class){
            var sources = SOURCES.get(server);
            source = sources == null || !isReference(target) ? null : sources.get(target.getUUID(TARGET));
        }
        CompoundTag frame = source == null ? null : source.frame(width, height);
        return frame == null ? new CompoundTag() : frame.copy();
    }
    public static CompoundTag frame(MinecraftServer server, CompoundTag target, int width, int height, net.minecraft.server.level.ServerPlayer player){
        java.util.function.Predicate<net.minecraft.server.level.ServerPlayer> access;
        synchronized(VirtualDisplaySources.class){ var permissions = ACCESS.get(server); access = permissions == null || !isReference(target) ? null : permissions.get(target.getUUID(TARGET)); }
        return access != null && access.test(player) ? frame(server, target, width, height) : new CompoundTag();
    }
    public static boolean input(MinecraftServer server, CompoundTag target, net.minecraft.server.level.ServerPlayer player, com.rieno.gadgetsandgizmos.lib.script.ScriptInput event){
        Input input;
        synchronized(VirtualDisplaySources.class){ var inputs = INPUTS.get(server); input = inputs == null || !isReference(target) ? null : inputs.get(target.getUUID(TARGET)); }
        return input != null && input.accept(player, event);
    }
    public static synchronized void clear(MinecraftServer server){ SOURCES.remove(server); INPUTS.remove(server); ACCESS.remove(server); }
}
