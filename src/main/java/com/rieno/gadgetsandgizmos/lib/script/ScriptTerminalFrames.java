package com.rieno.gadgetsandgizmos.lib.script;

import net.minecraft.server.MinecraftServer;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

// Associate extended monitor frames with host-owned targets on the owning server thread
public final class ScriptTerminalFrames{
    private static final Map<MinecraftServer, Map<Object, Entry>> FRAMES = new WeakHashMap<>();
    static{
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent evt) -> FRAMES.remove(evt.getServer()));
    }
    private ScriptTerminalFrames(){}
    public static void publish(MinecraftServer server, Object target, UUID owner, ScriptTerminal frame){ FRAMES.computeIfAbsent(server, ignored -> new WeakHashMap<>()).put(target, new Entry(owner, frame)); }
    public static ScriptTerminal frame(MinecraftServer server, Object target){ var frames = FRAMES.get(server); var entry = frames == null ? null : frames.get(target); return entry == null ? null : entry.frame(); }
    public static void release(MinecraftServer server, UUID owner){ var frames = FRAMES.get(server); if(frames != null) frames.values().removeIf(entry -> entry.owner().equals(owner)); }
    private record Entry(UUID owner, ScriptTerminal frame){}
}
