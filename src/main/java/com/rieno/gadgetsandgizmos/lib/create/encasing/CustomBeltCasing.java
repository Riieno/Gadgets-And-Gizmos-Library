package com.rieno.gadgetsandgizmos.lib.create.encasing;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

// Retain an optional material on a Create belt segment
public interface CustomBeltCasing{
    @Nullable ResourceLocation getCasingMaterial();

    // Clear the material with null when restoring Create's casing
    void setCasingMaterial(@Nullable ResourceLocation id);
}
