package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

// Describe one persistent resource movement requested by a controller or dock
public record WorkerTask(
        UUID id,
        String name,
        WorkerResourceKey resource,
        long requestedAmount,
        long completedAmount,
        int priority,
        boolean enabled
) {
    // Initialize the worker task
    public WorkerTask {
        id = id == null ? UUID.randomUUID() : id;
        name = name == null || name.isBlank() ? "Worker Task" : name.strip();
        if (name.length() > 64) name = name.substring(0, 64);
        resource = resource == null ? WorkerResourceKey.energy() : resource;
        requestedAmount = Math.max(0L, requestedAmount);
        completedAmount = Math.max(0L, Math.min(requestedAmount, completedAmount));
        priority = Math.max(-100, Math.min(100, priority));
    }

    // Get the remaining requested amount
    public long remainingAmount() {
        return Math.max(0L, requestedAmount - completedAmount);
    }

    // Check whether the task can be assigned
    public boolean pending() {
        return enabled && remainingAmount() > 0L;
    }

    // Copy the task with additional completed work
    public WorkerTask complete(long amount) {
        return new WorkerTask(id, name, resource, requestedAmount,
                completedAmount + Math.max(0L, amount), priority, enabled);
    }

    // Write the worker task
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Name", name);
        tag.put("Resource", resource.toTag());
        tag.putLong("Requested", requestedAmount);
        tag.putLong("Completed", completedAmount);
        tag.putInt("Priority", priority);
        tag.putBoolean("Enabled", enabled);
        return tag;
    }

    // Read the worker task
    public static WorkerTask fromTag(CompoundTag tag) {
        return new WorkerTask(
                tag != null && tag.hasUUID("Id") ? tag.getUUID("Id") : UUID.randomUUID(),
                tag == null ? "" : tag.getString("Name"),
                WorkerResourceKey.fromTag(tag == null ? new CompoundTag() : tag.getCompound("Resource")),
                tag == null ? 0L : tag.getLong("Requested"),
                tag == null ? 0L : tag.getLong("Completed"),
                tag == null ? 0 : tag.getInt("Priority"),
                tag != null && tag.getBoolean("Enabled"));
    }
}
