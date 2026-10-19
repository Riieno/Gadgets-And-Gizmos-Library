package com.rieno.gadgetsandgizmos.lib.worker;

import java.util.Locale;

// Identify the transferable resources understood by logistics workers
public enum WorkerResourceType {
    ITEM("item"),
    FLUID("fluid"),
    ENERGY("energy"),
    FUEL("fuel");

    private final String id;

    WorkerResourceType(String id) {
        this.id = id;
    }

    // Get the stable resource type id
    public String id() {
        return id;
    }

    // Resolve a resource type id
    public static WorkerResourceType fromId(String id) {
        String normalized = id == null ? "" : id.strip().toLowerCase(Locale.ROOT);
        for (WorkerResourceType type : values()) {
            if (type.id.equals(normalized)) return type;
        }
        return ITEM;
    }
}
