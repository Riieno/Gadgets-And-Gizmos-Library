package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Retain one active worker order and its planned task queue
public final class WorkerTaskQueue {
    private @Nullable WorkerWorkOrder current;
    private final ArrayDeque<WorkerWorkOrder> planned = new ArrayDeque<>();

    // Get the active order
    public @Nullable WorkerWorkOrder current() {
        return current;
    }

    // Get the planned orders in execution order
    public List<WorkerWorkOrder> planned() {
        return List.copyOf(planned);
    }

    // Add a unique order to the active slot or planned queue
    public boolean enqueue(WorkerWorkOrder order) {
        if (order == null || contains(order.id())) return false;
        if (current == null) current = order;
        else planned.addLast(order);
        return true;
    }

    // Replace the active order while retaining its queue position
    public void updateCurrent(WorkerWorkOrder order) {
        if (order == null) return;
        if (current == null || current.id().equals(order.id())) current = order;
    }

    // Complete the active order and promote the next planned order
    public @Nullable WorkerWorkOrder completeCurrent() {
        WorkerWorkOrder completed = current;
        current = planned.pollFirst();
        return completed;
    }

    // Cancel one active or planned order and preserve the remaining queue order
    public boolean cancel(UUID orderId) {
        if (orderId == null) return false;
        if (current != null && orderId.equals(current.id())) {
            current = planned.pollFirst();
            return true;
        }
        return planned.removeIf(order -> orderId.equals(order.id()));
    }

    // Clear every order
    public void clear() {
        current = null;
        planned.clear();
    }

    // Check whether an order is already active or planned
    public boolean contains(UUID orderId) {
        if (orderId == null) return false;
        if (current != null && orderId.equals(current.id())) return true;
        return planned.stream().anyMatch(order -> orderId.equals(order.id()));
    }

    // Serialize the task queue
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        if (current != null) tag.put("Current", current.toTag());
        ListTag plannedTags = new ListTag();
        for (WorkerWorkOrder order : planned) plannedTags.add(order.toTag());
        tag.put("Planned", plannedTags);
        return tag;
    }

    // Deserialize a task queue
    public static WorkerTaskQueue fromTag(CompoundTag tag) {
        WorkerTaskQueue queue = new WorkerTaskQueue();
        if (tag == null) return queue;
        if (tag.contains("Current", Tag.TAG_COMPOUND)) {
            queue.current = WorkerWorkOrder.fromTag(tag.getCompound("Current"));
        }
        ListTag plannedTags = tag.getList("Planned", Tag.TAG_COMPOUND);
        List<WorkerWorkOrder> restored = new ArrayList<>(plannedTags.size());
        for (int idx = 0; idx < plannedTags.size(); idx++) {
            restored.add(WorkerWorkOrder.fromTag(plannedTags.getCompound(idx)));
        }
        queue.planned.addAll(restored);
        return queue;
    }
}
