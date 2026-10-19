package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// Retain one active worker order and its planned task queue
public final class WorkerTaskQueue {
    private static final int MAX_COMPLETED_HISTORY = 128;
    private @Nullable WorkerWorkOrder current;
    private final ArrayDeque<WorkerWorkOrder> planned = new ArrayDeque<>();
    private final ArrayDeque<WorkerWorkOrder> completed = new ArrayDeque<>();
    private long completedWeight;
    private int completedTotal;

    // Get the active order
    public @Nullable WorkerWorkOrder current() {
        return current;
    }

    // Get the planned orders in execution order
    public List<WorkerWorkOrder> planned() {
        return List.copyOf(planned);
    }

    // Count the full future demand for one stocked result before skipping its producer
    public long plannedInputDemand(WorkerResourceKey resource){
        if(resource == null) return 0L;
        long demand = 0L;
        for(WorkerWorkOrder order : planned){
            if(order.recipePlan() == null){
                if(order.mode() == WorkerWorkOrder.Mode.TRANSFER && resource.equals(order.task().resource()))
                    demand = saturatedAdd(demand, order.task().remainingAmount());
                continue;
            }
            WorkerRecipePlan plan = order.recipePlan();
            long remaining = order.task().remainingAmount();
            if(remaining <= 0L) continue;
            long batches = (remaining - 1L) / plan.resultAmount() + 1L;
            long perBatch = plan.inputAmount(resource);
            if(perBatch <= 0L) continue;
            demand = saturatedAdd(demand, batches > Long.MAX_VALUE / perBatch
                    ? Long.MAX_VALUE : batches * perBatch);
        }
        return demand;
    }

    private static long saturatedAdd(long first, long second){
        return first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
    }

    // Get recently completed steps for worker progress displays
    public List<WorkerWorkOrder> completed(){ return List.copyOf(completed); }

    // Count all completed steps even when their detailed history is trimmed
    public long completedWeight(){ return completedWeight; }
    public int completedTotal(){ return completedTotal; }

    // Add a unique order to the active slot or planned queue
    public boolean enqueue(WorkerWorkOrder order) {
        if (order == null || contains(order.id())) return false;
        insertChain(List.of(order));
        return true;
    }

    // Add an ordered work chain without leaving a partially queued chain behind
    public boolean enqueueAll(List<WorkerWorkOrder> orders) {
        if (orders == null || orders.isEmpty()) return false;
        List<WorkerWorkOrder> chain = new ArrayList<>();
        for (WorkerWorkOrder order : orders) {
            if (order == null || contains(order.id())
                    || chain.stream().anyMatch(queued -> queued.id().equals(order.id()))) return false;
            chain.add(order);
        }
        insertChain(chain);
        return true;
    }

    // Insert a new request ahead of lower priority requests without splitting its recipe chain
    private void insertChain(List<WorkerWorkOrder> chain){
        if(current == null){
            completed.clear();
            completedWeight = 0L;
            completedTotal = 0;
            current = chain.getFirst();
            chain = chain.subList(1, chain.size());
        }
        if(chain.isEmpty()) return;
        List<WorkerWorkOrder> ordered = new ArrayList<>(planned);
        int idx = 0;
        while(idx < ordered.size() && ordered.get(idx).task().priority() >= chain.getFirst().task().priority()) idx++;
        ordered.addAll(idx, chain);
        planned.clear();
        planned.addAll(ordered);
    }

    // Run the highest priority independent step while machine outputs are pending
    public boolean yieldCurrent(Set<UUID> waiting){
        if(current == null || waiting == null || !waiting.contains(current.id())) return false;
        List<WorkerWorkOrder> orders = new ArrayList<>();
        orders.add(current);
        orders.addAll(planned);
        Set<WorkerResourceKey> blocked = new HashSet<>();
        WorkerWorkOrder selected = null;
        for(WorkerWorkOrder order : orders){
            boolean dependent = order.recipePlan() != null
                    ? order.recipePlan().inputs().stream().anyMatch(input -> blocked.contains(input.resource()))
                    : blocked.contains(order.task().resource());
            if(!waiting.contains(order.id()) && !dependent && !order.returningToStation()
                    && (selected == null || order.task().priority() > selected.task().priority())) selected = order;
            blocked.add(order.outputResource());
        }
        if(selected == null) return false;
        orders.remove(selected);
        current = selected;
        planned.clear();
        planned.addAll(orders);
        return true;
    }

    // Interrupt an idle step when a previously waiting machine has finished
    public boolean promote(UUID orderId){
        if(current == null || orderId == null || current.id().equals(orderId)) return false;
        WorkerWorkOrder selected = planned.stream().filter(order -> order.id().equals(orderId)).findFirst().orElse(null);
        if(selected == null) return false;
        planned.remove(selected);
        planned.addFirst(current);
        current = selected;
        return true;
    }

    // Suspend the active order until every prerequisite completes, retaining all later orders
    public boolean prepend(List<WorkerWorkOrder> orders){
        if(current == null || orders == null || orders.isEmpty()) return false;
        java.util.Set<UUID> ids = new java.util.HashSet<>();
        for(WorkerWorkOrder order : orders){
            if(order == null || contains(order.id()) || !ids.add(order.id())) return false;
        }
        planned.addFirst(current);
        for(int idx = orders.size() - 1; idx > 0; idx--) planned.addFirst(orders.get(idx));
        current = orders.getFirst();
        return true;
    }

    // Replace the active order while retaining its queue position
    public void updateCurrent(WorkerWorkOrder order) {
        if (order == null) return;
        if (current == null || current.id().equals(order.id())) current = order;
    }

    // Replace a failed production attempt without losing the jobs which follow it
    public boolean replaceCurrent(List<WorkerWorkOrder> orders){
        if(current == null || orders == null || orders.isEmpty()) return false;
        java.util.Set<UUID> ids = new java.util.HashSet<>();
        for(WorkerWorkOrder order : orders){
            if(order == null || !ids.add(order.id())
                    || planned.stream().anyMatch(queued -> queued.id().equals(order.id()))) return false;
        }
        for(int idx = orders.size() - 1; idx > 0; idx--) planned.addFirst(orders.get(idx));
        current = orders.getFirst();
        return true;
    }

    // Complete the active order and promote the next planned order
    public @Nullable WorkerWorkOrder completeCurrent() {
        WorkerWorkOrder completed = current;
        recordCompleted(completed);
        current = planned.pollFirst();
        return completed;
    }

    // Retain a finished step when its active slot becomes a separate return trip
    public void recordCompleted(@Nullable WorkerWorkOrder order){
        if(order == null) return;
        completed.addLast(order);
        while(completed.size() > MAX_COMPLETED_HISTORY) completed.pollFirst();
        long weight = Math.max(1L, order.task().requestedAmount());
        completedWeight = completedWeight > Long.MAX_VALUE - weight ? Long.MAX_VALUE : completedWeight + weight;
        if(completedTotal < Integer.MAX_VALUE) completedTotal++;
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
        completed.clear();
        completedWeight = 0L;
        completedTotal = 0;
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
        ListTag completedTags = new ListTag();
        for(WorkerWorkOrder order : completed) completedTags.add(order.toTag());
        tag.put("Completed", completedTags);
        tag.putLong("CompletedWeight", completedWeight);
        tag.putInt("CompletedTotal", completedTotal);
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
        ListTag completedTags = tag.getList("Completed", Tag.TAG_COMPOUND);
        for(int idx = Math.max(0, completedTags.size() - MAX_COMPLETED_HISTORY); idx < completedTags.size(); idx++){
            queue.completed.addLast(WorkerWorkOrder.fromTag(completedTags.getCompound(idx)));
        }
        queue.completedWeight = Math.max(0L, tag.getLong("CompletedWeight"));
        queue.completedTotal = Math.max(0, tag.getInt("CompletedTotal"));
        if(!tag.contains("CompletedWeight")){
            for(WorkerWorkOrder order : queue.completed){
                long weight = Math.max(1L, order.task().requestedAmount());
                queue.completedWeight = queue.completedWeight > Long.MAX_VALUE - weight
                        ? Long.MAX_VALUE : queue.completedWeight + weight;
            }
            queue.completedTotal = queue.completed.size();
        }
        return queue;
    }
}
