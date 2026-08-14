package com.rieno.gadgetsandgizmos.lib.display;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

// Register block types that can be selected as external ACC display sources
public final class AccDisplaySourceRegistry {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<Block, Entry> ENTRIES = new LinkedHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ACC display source
    private AccDisplaySourceRegistry() {
    }

    // Register the ACC display source
    public static synchronized void register(ResourceLocation id, Block block, AccDisplaySource source) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(source, "source");
        if (ENTRIES.containsKey(block)) {
            throw new IllegalStateException("ACC display source already registered: " + id);
        }
        ENTRIES.put(block, new Entry(id, source));
    }

    // Register the ACC display source if absent
    public static synchronized boolean registerIfAbsent(ResourceLocation id, Block block,
                                                         AccDisplaySource source) {
        if (ENTRIES.containsKey(block)) return false;
        register(id, block, source);
        return true;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Remove the ACC display source
    public static synchronized boolean unregister(Block block) {
        return block != null && ENTRIES.remove(block) != null;
    }

    // Check if this is a source
    public static boolean isSource(@Nullable BlockEntity blockEntity) {
        return entry(blockEntity) != null;
    }

    // Check if this is a source block ID
    public static boolean isSourceBlockId(String blockId) {
        ResourceLocation id = ResourceLocation.tryParse(blockId);
        return id != null && entry(BuiltInRegistries.BLOCK.get(id)) != null;
    }

    // Get the source
    public static @Nullable AccDisplaySource source(@Nullable BlockEntity blockEntity) {
        Entry entry = entry(blockEntity);
        return entry == null ? null : entry.source();
    }

    // Get the id
    public static @Nullable ResourceLocation id(@Nullable BlockEntity blockEntity) {
        Entry entry = entry(blockEntity);
        return entry == null ? null : entry.id();
    }

    // Get the entry
    private static synchronized @Nullable Entry entry(@Nullable BlockEntity blockEntity) {
        return blockEntity == null ? null : ENTRIES.get(blockEntity.getBlockState().getBlock());
    }

    // Get the entry
    private static synchronized @Nullable Entry entry(@Nullable Block block) {
        return block == null ? null : ENTRIES.get(block);
    }

    // Store the entry
    private record Entry(ResourceLocation id, AccDisplaySource source) {
    }
}
