package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Describe one assigned worker for remote configuration interfaces
public record WorkerStatusSnapshot(UUID workerId, String name, WorkerProfile.Job job, boolean enabled,
                                   @Nullable UUID subLevelId, BlockPos position, String status,
                                   String skinVariant, @Nullable WorkerWorkOrder currentOrder,
                                   List<WorkerWorkOrder> plannedOrders) {
    // Normalize worker status values
    public WorkerStatusSnapshot {
        workerId = workerId == null ? UUID.randomUUID() : workerId;
        name = name == null || name.isBlank() ? "Worker" : name;
        job = job == null ? WorkerProfile.Job.ANY : job;
        position = position == null ? BlockPos.ZERO : position.immutable();
        status = status == null ? "" : status;
        skinVariant = skinVariant == null ? "" : skinVariant;
        plannedOrders = plannedOrders == null ? List.of() : List.copyOf(plannedOrders);
    }

    // Serialize the worker status
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Worker", workerId);
        tag.putString("Name", name);
        tag.putString("Job", job.id());
        tag.putBoolean("Enabled", enabled);
        if (subLevelId != null) tag.putUUID("SubLevel", subLevelId);
        tag.putLong("Position", position.asLong());
        tag.putString("Status", status);
        tag.putString("SkinVariant", skinVariant);
        if (currentOrder != null) tag.put("Current", currentOrder.toTag());
        ListTag planned = new ListTag();
        for (WorkerWorkOrder order : plannedOrders) planned.add(order.toTag());
        tag.put("Planned", planned);
        return tag;
    }

    // Deserialize worker status
    public static WorkerStatusSnapshot fromTag(CompoundTag tag) {
        UUID workerId = tag.hasUUID("Worker") ? tag.getUUID("Worker") : UUID.randomUUID();
        UUID subLevelId = tag.hasUUID("SubLevel") ? tag.getUUID("SubLevel") : null;
        WorkerWorkOrder current = tag.contains("Current", Tag.TAG_COMPOUND)
                ? WorkerWorkOrder.fromTag(tag.getCompound("Current")) : null;
        ListTag plannedTags = tag.getList("Planned", Tag.TAG_COMPOUND);
        List<WorkerWorkOrder> planned = new ArrayList<>(plannedTags.size());
        for (int idx = 0; idx < plannedTags.size(); idx++) {
            planned.add(WorkerWorkOrder.fromTag(plannedTags.getCompound(idx)));
        }
        return new WorkerStatusSnapshot(workerId, tag.getString("Name"),
                WorkerProfile.Job.fromId(tag.getString("Job")), tag.getBoolean("Enabled"), subLevelId,
                BlockPos.of(tag.getLong("Position")), tag.getString("Status"),
                tag.getString("SkinVariant"), current, planned);
    }
}
