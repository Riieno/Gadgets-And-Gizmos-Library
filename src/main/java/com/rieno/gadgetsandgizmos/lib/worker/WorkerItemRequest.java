package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

// Request a transfer or recipe chain from a controller's managed workers
public record WorkerItemRequest(UUID id, ResourceLocation itemId, int amount, boolean craft,
                                String taskName, UUID destinationId, @Nullable UUID selectedWorkerId){
    public WorkerItemRequest{
        id = id == null ? UUID.randomUUID() : id;
        if(itemId == null || amount < 1 || amount > 1_000_000) throw new IllegalArgumentException("Invalid worker item request");
        taskName = taskName == null ? "" : taskName.strip();
        if(taskName.length() > 64) throw new IllegalArgumentException("Worker task name is too long");
    }

    // Preserve automatic assignment for existing callers
    public WorkerItemRequest(UUID id, ResourceLocation itemId, int amount, boolean craft,
                             String taskName, UUID destinationId){
        this(id, itemId, amount, craft, taskName, destinationId, null);
    }
}
