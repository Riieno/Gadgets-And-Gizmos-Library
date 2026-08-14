package com.rieno.gadgetsandgizmos.lib.shipping;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

// Describe one named wireless resource run shared across a ship
public record ShipLogisticsRun(
        UUID id,
        String name,
        ResourceType resourceType,
        List<Endpoint> endpoints
) {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final UUID ROOT_LEVEL = new UUID(0L, 0L);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ship logistics run
    public ShipLogisticsRun {
        id = id == null ? UUID.randomUUID() : id;
        resourceType = resourceType == null ? ResourceType.ITEM : resourceType;
        String fallbackName = resourceType.label() + " Run";
        name = name == null || name.isBlank() ? fallbackName : name.strip();
        if (name.length() > 64) name = name.substring(0, 64);
        Map<String, Endpoint> unique = new LinkedHashMap<>();
        for (Endpoint endpoint : endpoints == null ? List.<Endpoint>of() : endpoints) {
            if (endpoint != null) unique.put(endpoint.key(), endpoint);
        }
        endpoints = unique.values().stream()
                .sorted(Comparator.comparing((Endpoint endpoint) -> endpoint.subLevelId().toString())
                        .thenComparingLong(endpoint -> endpoint.position().asLong()))
                .toList();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the ship logistics run
    public static ShipLogisticsRun create(
            ResourceType resourceType, String name, List<Endpoint> endpoints
    ) {
        return new ShipLogisticsRun(UUID.randomUUID(), name, resourceType, endpoints);
    }

    // Copy the ship logistics run with the name
    public ShipLogisticsRun withName(String nextName) {
        return new ShipLogisticsRun(id, nextName, resourceType, endpoints);
    }

    // Copy the ship logistics run with the endpoints
    public ShipLogisticsRun withEndpoints(List<Endpoint> nextEndpoints) {
        return new ShipLogisticsRun(id, name, resourceType, nextEndpoints);
    }

    // Check if this contains the value
    public boolean contains(UUID subLevelId, BlockPos position) {
        Endpoint target = new Endpoint(subLevelId, position);
        return endpoints.stream().anyMatch(target::equals);
    }

    // Check if this is a fuel run
    public boolean isFuelRun() {
        return resourceType == ResourceType.FUEL;
    }

    // Write the ship logistics run data
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Name", name);
        tag.putString("Resource", resourceType.id());
        ListTag endpointTags = new ListTag();
        for (Endpoint endpoint : endpoints) endpointTags.add(endpoint.toTag());
        tag.put("Endpoints", endpointTags);
        return tag;
    }

    // Read the ship logistics run data
    public static @Nullable ShipLogisticsRun fromTag(CompoundTag tag) {
        if (tag == null || !tag.hasUUID("Id")) return null;
        ResourceType resourceType = ResourceType.fromId(tag.getString("Resource"));
        ListTag endpointTags = tag.getList("Endpoints", Tag.TAG_COMPOUND);
        java.util.ArrayList<Endpoint> endpoints = new java.util.ArrayList<>();
        for (int idx = 0; idx < endpointTags.size(); idx++) {
            Endpoint endpoint = Endpoint.fromTag(endpointTags.getCompound(idx));
            if (endpoint != null) endpoints.add(endpoint);
        }
        return new ShipLogisticsRun(tag.getUUID("Id"), tag.getString("Name"),
                resourceType, endpoints);
    }

    // Define the resource type values
    public enum ResourceType {
        ITEM("item", "Item", 0xFF55D6FF),
        FLUID("fluid", "Fluid", 0xFF4A8DFF),
        ENERGY("energy", "FE", 0xFFFFD54F),
        FUEL("fuel", "Fuel", 0xFFFF8A3D);

        // Resource type id
        private final String id;
        // Display label
        private final String label;
        // Outline color
        private final int outlineColor;

        // Initialize the resource type
        ResourceType(String id, String label, int outlineColor) {
            this.id = id;
            this.label = label;
            this.outlineColor = outlineColor;
        }

        // Get the id
        public String id() {
            return id;
        }

        // Get the label
        public String label() {
            return label;
        }

        // Get the outline color
        public int outlineColor() {
            return outlineColor;
        }

        // Create the resource type from id
        public static ResourceType fromId(String id) {
            String normalized = id == null ? "" : id.strip().toLowerCase(Locale.ROOT);
            for (ResourceType type : values()) {
                if (type.id.equals(normalized)) return type;
            }
            return ITEM;
        }
    }

    // Store the endpoint
    public record Endpoint(UUID subLevelId, BlockPos position) {
        // Initialize the endpoint
        public Endpoint {
            subLevelId = subLevelId == null ? ROOT_LEVEL : subLevelId;
            position = position == null ? BlockPos.ZERO : position.immutable();
        }

        // Handle key
        public String key() {
            return subLevelId + ":" + position.asLong();
        }

        // Write the endpoint data
        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("SubLevel", subLevelId);
            tag.putLong("Pos", position.asLong());
            return tag;
        }

        // Read the endpoint data
        public static @Nullable Endpoint fromTag(CompoundTag tag) {
            if (tag == null || !tag.hasUUID("SubLevel")
                    || !tag.contains("Pos", Tag.TAG_LONG)) return null;
            return new Endpoint(tag.getUUID("SubLevel"), BlockPos.of(tag.getLong("Pos")));
        }
    }
}
