package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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
        long outputAmount,
        @Nullable UUID returnStationId,
        @Nullable WorkerRecipePlan recipePlan
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

    // Initialize an order without a return station for existing callers
    public WorkerWorkOrder(UUID id, WorkerTask task, Mode mode,
                           @Nullable UUID sourceEndpointId, @Nullable UUID destinationEndpointId,
                           @Nullable UUID processorEndpointId, WorkerResourceKey outputResource,
                           long outputAmount) {
        this(id, task, mode, sourceEndpointId, destinationEndpointId, processorEndpointId,
                outputResource, outputAmount, null, null);
    }

    // Initialize an order with a station return for existing callers
    public WorkerWorkOrder(UUID id, WorkerTask task, Mode mode,
                           @Nullable UUID sourceEndpointId, @Nullable UUID destinationEndpointId,
                           @Nullable UUID processorEndpointId, WorkerResourceKey outputResource,
                           long outputAmount, @Nullable UUID returnStationId) {
        this(id, task, mode, sourceEndpointId, destinationEndpointId, processorEndpointId,
                outputResource, outputAmount, returnStationId, null);
    }

    // Create an automatic transfer order
    public static WorkerWorkOrder automatic(WorkerTask task) {
        return new WorkerWorkOrder(null, task, Mode.TRANSFER,
                null, null, null, task == null ? null : task.resource(), 0L);
    }

    // Create an order which returns a worker to a named station
    public static WorkerWorkOrder returnToStation(UUID id, WorkerTask task, UUID stationId) {
        return new WorkerWorkOrder(id, task, Mode.RETURN_TO_STATION,
                null, null, null, task == null ? null : task.resource(), 0L, stationId, null);
    }

    // Copy the order with updated task progress
    public WorkerWorkOrder withTask(WorkerTask updatedTask) {
        return new WorkerWorkOrder(id, updatedTask, mode, sourceEndpointId,
                destinationEndpointId, processorEndpointId, outputResource, outputAmount, returnStationId,
                recipePlan);
    }

    // Copy the order with a station to return to after completion
    public WorkerWorkOrder withReturnStation(UUID stationId) {
        return new WorkerWorkOrder(id, task, mode, sourceEndpointId, destinationEndpointId,
                processorEndpointId, outputResource, outputAmount, stationId, recipePlan);
    }

    // Copy the order with an exact multi-resource recipe plan.
    public WorkerWorkOrder withRecipePlan(@Nullable WorkerRecipePlan plan) {
        return new WorkerWorkOrder(id, task, mode, sourceEndpointId, destinationEndpointId,
                processorEndpointId, outputResource, outputAmount, returnStationId, plan);
    }

    // Bind the final operation to the machine selected by a consumer
    public WorkerWorkOrder withProcessor(@Nullable UUID processorId){
        return new WorkerWorkOrder(id, task, mode, sourceEndpointId, destinationEndpointId,
                processorId, outputResource, outputAmount, returnStationId, recipePlan);
    }

    // Check whether this order uses an intermediate processor
    public boolean processing() {
        return mode == Mode.PROCESS || mode == Mode.AUTO_CRAFT;
    }

    // Check whether this order returns the worker to a station
    public boolean returningToStation() {
        return mode == Mode.RETURN_TO_STATION && returnStationId != null;
    }

    // Check whether this order has a station return destination
    public boolean returnsToStation() {
        return returnStationId != null;
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
        if (returnStationId != null) tag.putUUID("ReturnStation", returnStationId);
        if (recipePlan != null) tag.put("RecipePlan", recipePlan.toTag());
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
                safe.getLong("OutputAmount"),
                safe.hasUUID("ReturnStation") ? safe.getUUID("ReturnStation") : null,
                safe.contains("RecipePlan", Tag.TAG_COMPOUND)
                        ? WorkerRecipePlan.fromTag(safe.getCompound("RecipePlan")) : null);
    }

    public enum Mode {
        TRANSFER("transfer"),
        PROCESS("process"),
        AUTO_CRAFT("auto_craft"),
        FILL_CONTAINER("fill_container"),
        RECLAIM_INPUT("reclaim_input"),
        FROG_PORT("frog_port"),
        RETURN_TO_STATION("return_to_station");

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
