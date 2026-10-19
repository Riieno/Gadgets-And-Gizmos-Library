package com.rieno.gadgetsandgizmos.lib.worker;

// Describe the usable cargo capacity of one logistics worker.
public record WorkerCarryCapacity(long items, long fluids, long energy) {
    // Normalize every transferable capacity.
    public WorkerCarryCapacity {
        items = Math.max(0L, items);
        fluids = Math.max(0L, fluids);
        energy = Math.max(0L, energy);
    }

    // Get the capacity for one worker resource type.
    public long amountFor(WorkerResourceType type) {
        if (type == null) return 0L;
        return switch (type) {
            case ITEM -> items;
            case FLUID, FUEL -> fluids;
            case ENERGY -> energy;
        };
    }
}
