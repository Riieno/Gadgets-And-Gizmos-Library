package com.rieno.gadgetsandgizmos.lib.physics.archive;

import net.minecraft.nbt.CompoundTag;

// Let detached material previews omit programs and other state unrelated to their item costs
public interface SchematicMaterialData{
    // Return independent load data while retaining every field used by the entity's requirements
    CompoundTag schematicMaterialData(CompoundTag saved);
}
