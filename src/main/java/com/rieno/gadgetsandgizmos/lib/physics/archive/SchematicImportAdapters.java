package com.rieno.gadgetsandgizmos.lib.physics.archive;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

// Adapt mod-owned foreign references before the common safe NBT conversion
public final class SchematicImportAdapters{
    private static final Map<ResourceLocation, Adapter> ADAPTERS = new LinkedHashMap<>();

    private SchematicImportAdapters(){}

    // Register one adapter under its owning mod's namespace during common setup
    public static synchronized void register(ResourceLocation id, Adapter adapter){
        if(ADAPTERS.putIfAbsent(id, adapter) != null) throw new IllegalArgumentException("Schematic import adapter is already registered: " + id);
    }

    // Apply registered adapters to an independent configuration tag
    static CompoundTag prepare(SubLevelSchematic schematic, SubLevelSchematic.Body body, SubLevelSchematic.Block block){
        CompoundTag data = block.data();
        if(body.importFrame().format() != SubLevelSchematic.Format.NATIVE){
            for(Adapter adapter : ADAPTERS.values()) adapter.prepare(schematic, body, block, data);
        }
        return data;
    }

    // Convert owned references into template identities and local positions
    @FunctionalInterface
    public interface Adapter{
        void prepare(SubLevelSchematic schematic, SubLevelSchematic.Body body, SubLevelSchematic.Block block, CompoundTag data);
    }
}
