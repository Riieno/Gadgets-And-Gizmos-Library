package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

// Identify one item, fluid, energy or fuel resource without owning a storage implementation
public record WorkerResourceKey(WorkerResourceType type, ResourceLocation id) {
    public static final ResourceLocation ENERGY_ID =
            ResourceLocation.fromNamespaceAndPath("neoforge", "energy");

    // Initialize the worker resource key
    public WorkerResourceKey {
        type = type == null ? WorkerResourceType.ITEM : type;
        id = id == null ? defaultId(type) : id;
    }

    // Create an energy resource key
    public static WorkerResourceKey energy() {
        return new WorkerResourceKey(WorkerResourceType.ENERGY, ENERGY_ID);
    }

    // Write the resource key
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", type.id());
        tag.putString("Id", id.toString());
        return tag;
    }

    // Read the resource key
    public static WorkerResourceKey fromTag(CompoundTag tag) {
        WorkerResourceType type = WorkerResourceType.fromId(tag == null ? "" : tag.getString("Type"));
        ResourceLocation id = tag == null ? null : ResourceLocation.tryParse(tag.getString("Id"));
        return new WorkerResourceKey(type, id);
    }

    // Get the default resource id
    private static ResourceLocation defaultId(WorkerResourceType type) {
        if (type == WorkerResourceType.ENERGY) return ENERGY_ID;
        return ResourceLocation.fromNamespaceAndPath("minecraft", "air");
    }
}
