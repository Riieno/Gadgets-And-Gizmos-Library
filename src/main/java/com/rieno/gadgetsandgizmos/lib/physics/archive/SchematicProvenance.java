package com.rieno.gadgetsandgizmos.lib.physics.archive;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

// Keep producer identity separate from the coordinate format used to import blocks
public final class SchematicProvenance{
    public static final String SOURCE_TAG = "gadgetsngizmos:source";
    public static final ResourceLocation UNKNOWN = ResourceLocation.fromNamespaceAndPath("gadgetsngizmos", "unknown");
    public static final ResourceLocation PHOTOMANCY = ResourceLocation.fromNamespaceAndPath("photomancy", "schematic");
    public static final ResourceLocation TOOLGUN = ResourceLocation.fromNamespaceAndPath("enxv_aeronautics", "toolgun");

    private SchematicProvenance(){}

    // Require an explicit producer tag for portable native structures
    public static ResourceLocation source(CompoundTag tag, SubLevelSchematic.Format format){
        if(format == SubLevelSchematic.Format.PHOTOMANCY) return PHOTOMANCY;
        if(format == SubLevelSchematic.Format.TOOLGUN) return TOOLGUN;
        ResourceLocation source = tag.contains(SOURCE_TAG, Tag.TAG_STRING)
                ? ResourceLocation.tryParse(tag.getString(SOURCE_TAG)) : null;
        return source == null ? UNKNOWN : source;
    }
}
