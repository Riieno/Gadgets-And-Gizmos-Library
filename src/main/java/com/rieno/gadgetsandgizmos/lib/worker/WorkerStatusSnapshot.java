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
                                   List<WorkerWorkOrder> plannedOrders, List<WorkerWorkOrder> completedOrders,
                                   List<UUID> waitingOrders, long completedWeight, int completedTotal) {
    // Normalize worker status values
    public WorkerStatusSnapshot {
        workerId = workerId == null ? UUID.randomUUID() : workerId;
        name = name == null || name.isBlank() ? "Worker" : name;
        job = job == null ? WorkerProfile.Job.ANY : job;
        position = position == null ? BlockPos.ZERO : position.immutable();
        status = status == null ? "" : status;
        skinVariant = skinVariant == null ? "" : skinVariant;
        plannedOrders = plannedOrders == null ? List.of() : List.copyOf(plannedOrders);
        completedOrders = completedOrders == null ? List.of() : List.copyOf(completedOrders);
        waitingOrders = waitingOrders == null ? List.of() : List.copyOf(waitingOrders);
        completedWeight = Math.max(0L, completedWeight);
        completedTotal = Math.max(0, completedTotal);
    }

    // Initialize detailed status without a separate historical aggregate
    public WorkerStatusSnapshot(UUID workerId, String name, WorkerProfile.Job job, boolean enabled,
                                @Nullable UUID subLevelId, BlockPos position, String status,
                                String skinVariant, @Nullable WorkerWorkOrder currentOrder,
                                List<WorkerWorkOrder> plannedOrders, List<WorkerWorkOrder> completedOrders,
                                List<UUID> waitingOrders){
        this(workerId, name, job, enabled, subLevelId, position, status, skinVariant, currentOrder,
                plannedOrders, completedOrders, waitingOrders,
                completedOrders == null ? 0L : completedOrders.stream()
                        .mapToLong(order -> Math.max(1L, order.task().requestedAmount())).sum(),
                completedOrders == null ? 0 : completedOrders.size());
    }

    // Preserve the existing snapshot constructor for other worker displays
    public WorkerStatusSnapshot(UUID workerId, String name, WorkerProfile.Job job, boolean enabled,
                                @Nullable UUID subLevelId, BlockPos position, String status,
                                String skinVariant, @Nullable WorkerWorkOrder currentOrder,
                                List<WorkerWorkOrder> plannedOrders){
        this(workerId, name, job, enabled, subLevelId, position, status, skinVariant,
                currentOrder, plannedOrders, List.of(), List.of(), 0L, 0);
    }

    // Serialize the worker status
    public CompoundTag toTag() {
        return toTag(Integer.MAX_VALUE);
    }

    // Bound planned orders when sending worker status to compact remote screens
    public CompoundTag toTag(int maximumPlannedOrders) {
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
        for (int idx = 0; idx < plannedOrders.size() && idx < Math.max(0, maximumPlannedOrders); idx++) planned.add(plannedOrders.get(idx).toTag());
        tag.putInt("PlannedTotal", plannedOrders.size());
        tag.put("Planned", planned);
        ListTag completed = new ListTag();
        for(int idx = Math.max(0, completedOrders.size() - Math.max(0, maximumPlannedOrders));
            idx < completedOrders.size(); idx++) completed.add(completedOrders.get(idx).toTag());
        tag.put("Completed", completed);
        tag.putLong("CompletedWeight", completedWeight);
        tag.putInt("CompletedTotal", completedTotal);
        ListTag waiting = new ListTag();
        for(UUID id : waitingOrders){
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            waiting.add(entry);
        }
        tag.put("Waiting", waiting);
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
        ListTag completedTags = tag.getList("Completed", Tag.TAG_COMPOUND);
        List<WorkerWorkOrder> completed = new ArrayList<>(completedTags.size());
        for(int idx = 0; idx < completedTags.size(); idx++) completed.add(WorkerWorkOrder.fromTag(completedTags.getCompound(idx)));
        ListTag waitingTags = tag.getList("Waiting", Tag.TAG_COMPOUND);
        List<UUID> waiting = new ArrayList<>(waitingTags.size());
        for(int idx = 0; idx < waitingTags.size(); idx++){
            CompoundTag entry = waitingTags.getCompound(idx);
            if(entry.hasUUID("Id")) waiting.add(entry.getUUID("Id"));
        }
        return new WorkerStatusSnapshot(workerId, tag.getString("Name"),
                WorkerProfile.Job.fromId(tag.getString("Job")), tag.getBoolean("Enabled"), subLevelId,
                BlockPos.of(tag.getLong("Position")), tag.getString("Status"),
                tag.getString("SkinVariant"), current, planned, completed, waiting,
                tag.contains("CompletedWeight") ? tag.getLong("CompletedWeight") : completed.stream()
                        .mapToLong(order -> Math.max(1L, order.task().requestedAmount())).sum(),
                tag.contains("CompletedTotal") ? tag.getInt("CompletedTotal") : completed.size());
    }
}
