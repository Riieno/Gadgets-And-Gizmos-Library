package com.rieno.gadgetsandgizmos.lib.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// Share registry-backed item choices between client pickers and server ghost-slot validation
public final class RegisteredItemCatalog{
    // Keep registry access on the static API
    private RegisteredItemCatalog(){}

    // Include registered block items and mod items while excluding the empty item
    public static List<Item> items(){
        return BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR).toList();
    }

    // Accept ghost references without requiring ownership of a physical stack
    public static boolean contains(@Nullable ResourceLocation id){
        return id != null && BuiltInRegistries.ITEM.getOptional(id).filter(item -> item != Items.AIR).isPresent();
    }
}
