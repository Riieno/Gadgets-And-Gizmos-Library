package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

// Describe endpoint routing for transfer, processing and crafting worker tasks
public record WorkerWorkOrder(
        UUID id,
        WorkerTask task,
        Mode mode,
        @Nullable UUID sourceEndpointId,
        @Nullable UUID destinationEndpointId,
        @Nullable UUID processorEndpointId,
        WorkerResourceKey outputResource,
        long outputAmount
) {
    // Initialize the worker work order
    public WorkerWorkOrder {
        id = id == null ? UUID.randomUUID() : id;
        task = task == null ? new WorkerTask(null, "Worker Task", null,
                0L, 0L, 0, false) : task;
        mode = mode == null ? Mode.TRANSFER : mode;
        outputResource = outputResource == null ? task.resource() : outputResource;
        outputAmount = Math.max(0L, outputAmount);
    }

    // Create an automatic transfer order
    public static WorkerWorkOrder automatic(WorkerTask task) {
        return new WorkerWorkOrder(null, task, Mode.TRANSFER,
                null, null, null, task == null ? null : task.resource(), 0L);
    }

    // Copy the order with updated task progress
    public WorkerWorkOrder withTask(WorkerTask updatedTask) {
        return new WorkerWorkOrder(id, updatedTask, mode, sourceEndpointId,
                destinationEndpointId, processorEndpointId, outputResource, outputAmount);
    }

    // Check whether this order uses an intermediate processor
    public boolean processing() {
        return mode == Mode.PROCESS || mode == Mode.AUTO_CRAFT;
    }

    // Write the work order
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.put("Task", task.toTag());
        tag.putString("Mode", mode.id());
        if (sourceEndpointId != null) tag.putUUID("Source", sourceEndpointId);
        if (destinationEndpointId != null) tag.putUUID("Destination", destinationEndpointId);
        if (processorEndpointId != null) tag.putUUID("Processor", processorEndpointId);
        tag.put("Output", outputResource.toTag());
        tag.putLong("OutputAmount", outputAmount);
        return tag;
    }

    // Read the work order
    public static WorkerWorkOrder fromTag(CompoundTag tag) {
        CompoundTag safe = tag == null ? new CompoundTag() : tag;
        WorkerTask task = WorkerTask.fromTag(safe.getCompound("Task"));
        return new WorkerWorkOrder(
                safe.hasUUID("Id") ? safe.getUUID("Id") : UUID.randomUUID(),
                task,
                Mode.byId(safe.getString("Mode")),
                safe.hasUUID("Source") ? safe.getUUID("Source") : null,
                safe.hasUUID("Destination") ? safe.getUUID("Destination") : null,
                safe.hasUUID("Processor") ? safe.getUUID("Processor") : null,
                safe.contains("Output") ? WorkerResourceKey.fromTag(safe.getCompound("Output")) : task.resource(),
                safe.getLong("OutputAmount"));
    }

    public enum Mode {
        TRANSFER("transfer"),
        PROCESS("process"),
        AUTO_CRAFT("auto_craft"),
        FROG_PORT("frog_port");

        private final String id;

        // Initialize the work order mode
        Mode(String id) {
            this.id = id;
        }

        // Get the serialized id
        public String id() {
            return id;
        }

        // Find a work order mode by id
        public static Mode byId(String id) {
            if (id != null) {
                for (Mode mode : values()) if (mode.id.equalsIgnoreCase(id)) return mode;
            }
            return TRANSFER;
        }
    }
}
