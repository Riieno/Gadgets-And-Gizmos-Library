package com.rieno.gadgetsandgizmos.lib.compat;

import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Keep loaded world power sources available to Physics Staff tracking
public final class PhysicsStaffWorldPowerRegistry {
    private static final Map<MinecraftServer, Map<UUID, Set<PhysicsStaffWorldPowerSource>>> SOURCES = new HashMap<>();

    private PhysicsStaffWorldPowerRegistry() {
    }

    // Register a loaded world power source
    public static void register(MinecraftServer server, PhysicsStaffWorldPowerSource source) {
        if (server == null || source == null || source.physicsStaffId() == null) return;
        SOURCES.computeIfAbsent(server, ignored -> new HashMap<>())
                .computeIfAbsent(source.physicsStaffId(), ignored -> new LinkedHashSet<>())
                .add(source);
    }

    // Unregister an unloaded world power source
    public static void unregister(MinecraftServer server, PhysicsStaffWorldPowerSource source) {
        if (server == null || source == null || source.physicsStaffId() == null) return;
        Map<UUID, Set<PhysicsStaffWorldPowerSource>> byStaff = SOURCES.get(server);
        if (byStaff == null) return;
        Set<PhysicsStaffWorldPowerSource> sources = byStaff.get(source.physicsStaffId());
        if (sources == null) return;
        sources.remove(source);
        if (sources.isEmpty()) byStaff.remove(source.physicsStaffId());
        if (byStaff.isEmpty()) SOURCES.remove(server);
    }

    // Find the loaded source for a staff
    public static PhysicsStaffWorldPowerSource find(MinecraftServer server, UUID staffId) {
        if (server == null || staffId == null) return null;
        Map<UUID, Set<PhysicsStaffWorldPowerSource>> byStaff = SOURCES.get(server);
        if (byStaff == null) return null;
        Set<PhysicsStaffWorldPowerSource> sources = byStaff.get(staffId);
        return sources == null || sources.isEmpty() ? null : sources.iterator().next();
    }

    // Clear server-owned sources during shutdown
    public static void clear(MinecraftServer server) {
        if (server != null) SOURCES.remove(server);
    }
}
