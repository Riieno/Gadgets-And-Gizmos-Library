package com.rieno.gadgetsandgizmos.lib.display;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

// Register dynamic external connections for ACC display adapters
public final class AccDisplayConnectionRegistry {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<ResourceLocation, Predicate<BlockEntity>> SOURCES = new LinkedHashMap<>();
    private static final Map<ResourceLocation, Predicate<BlockEntity>> TARGETS = new LinkedHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ACC display connection registry
    private AccDisplayConnectionRegistry() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Register the dynamic display source
    public static synchronized void registerSource(ResourceLocation id,
                                                   Predicate<BlockEntity> predicate) {
        register(SOURCES, id, predicate, "source");
    }

    // Register the dynamic display source if absent
    public static synchronized boolean registerSourceIfAbsent(ResourceLocation id,
                                                              Predicate<BlockEntity> predicate) {
        return registerIfAbsent(SOURCES, id, predicate);
    }

    // Register the dynamic display target
    public static synchronized void registerTarget(ResourceLocation id,
                                                   Predicate<BlockEntity> predicate) {
        register(TARGETS, id, predicate, "target");
    }

    // Register the dynamic display target if absent
    public static synchronized boolean registerTargetIfAbsent(ResourceLocation id,
                                                              Predicate<BlockEntity> predicate) {
        return registerIfAbsent(TARGETS, id, predicate);
    }

    // Remove the dynamic display connection
    public static synchronized boolean unregister(ResourceLocation id) {
        return id != null && (SOURCES.remove(id) != null || TARGETS.remove(id) != null);
    }

    // Check if this is a display source
    public static boolean isSource(@Nullable BlockEntity blockEntity) {
        return matches(SOURCES, blockEntity);
    }

    // Check if this is a display target
    public static boolean isTarget(@Nullable BlockEntity blockEntity) {
        return matches(TARGETS, blockEntity);
    }

    // Check if this is a display connection
    public static boolean isConnection(@Nullable BlockEntity blockEntity) {
        return isSource(blockEntity) || isTarget(blockEntity);
    }

    // Register the connection predicate
    private static void register(Map<ResourceLocation, Predicate<BlockEntity>> entries,
                                 ResourceLocation id, Predicate<BlockEntity> predicate,
                                 String kind) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(predicate, "predicate");
        if (entries.containsKey(id)) {
            throw new IllegalStateException("ACC display " + kind + " already registered: " + id);
        }
        entries.put(id, predicate);
    }

    // Register the connection predicate if absent
    private static boolean registerIfAbsent(Map<ResourceLocation, Predicate<BlockEntity>> entries,
                                            ResourceLocation id,
                                            Predicate<BlockEntity> predicate) {
        if (entries.containsKey(id)) {
            return false;
        }
        register(entries, id, predicate, entries == SOURCES ? "source" : "target");
        return true;
    }

    // Check the registered connection predicates
    private static synchronized boolean matches(Map<ResourceLocation, Predicate<BlockEntity>> entries,
                                                @Nullable BlockEntity blockEntity) {
        if (blockEntity == null) {
            return false;
        }
        for (Predicate<BlockEntity> predicate : entries.values()) {
            try {
                if (predicate.test(blockEntity)) {
                    return true;
                }
            } catch (RuntimeException ignored) {
            }
        }
        return false;
    }
}
