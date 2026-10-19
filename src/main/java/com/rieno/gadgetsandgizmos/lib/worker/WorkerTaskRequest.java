package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

// Describe one externally requested worker task without coupling it to a graph implementation
public record WorkerTaskRequest(UUID id, String taskName, Set<UUID> workers, int priority,
                                InterruptPolicy interruptPolicy, boolean resumePrevious,
                                CompoundTag parameters) {
    // Initialize one task request
    public WorkerTaskRequest {
        id = id == null ? UUID.randomUUID() : id;
        taskName = taskName == null || taskName.isBlank() ? "Worker Task" : taskName.strip();
        if (taskName.length() > 64) taskName = taskName.substring(0, 64);
        workers = workers == null ? Set.of() : Set.copyOf(workers);
        priority = Math.max(-100, Math.min(100, priority));
        interruptPolicy = interruptPolicy == null ? InterruptPolicy.USE_TASK_DEFAULT : interruptPolicy;
        parameters = parameters == null ? new CompoundTag() : parameters.copy();
    }

    // Check whether this request can choose any available worker
    public boolean automaticWorkerSelection() {
        return workers.isEmpty();
    }

    // Write the task request
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Task", taskName);
        ListTag ids = new ListTag();
        workers.forEach(worker -> ids.add(StringTag.valueOf(worker.toString())));
        tag.put("Workers", ids);
        tag.putInt("Priority", priority);
        tag.putString("Interrupt", interruptPolicy.id());
        tag.putBoolean("ResumePrevious", resumePrevious);
        if (!parameters.isEmpty()) tag.put("Parameters", parameters.copy());
        return tag;
    }

    // Read the task request
    public static WorkerTaskRequest fromTag(CompoundTag tag) {
        CompoundTag safe = tag == null ? new CompoundTag() : tag;
        Set<UUID> workers = new LinkedHashSet<>();
        ListTag ids = safe.getList("Workers", Tag.TAG_STRING);
        for (int index = 0; index < ids.size(); index++) {
            try {
                workers.add(UUID.fromString(ids.getString(index)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return new WorkerTaskRequest(safe.hasUUID("Id") ? safe.getUUID("Id") : null,
                safe.getString("Task"), workers, safe.getInt("Priority"),
                InterruptPolicy.fromId(safe.getString("Interrupt")), safe.getBoolean("ResumePrevious"),
                safe.getCompound("Parameters"));
    }

    public enum InterruptPolicy {
        USE_TASK_DEFAULT("use_task_default"),
        INTERRUPT_CURRENT("interrupt_current"),
        QUEUE("queue"),
        ONLY_WHEN_IDLE("only_when_idle");

        private final String id;

        // Initialize the interrupt policy
        InterruptPolicy(String id) {
            this.id = id;
        }

        // Get the stable serialized id
        public String id() {
            return id;
        }

        // Resolve one interrupt policy from its serialized id
        public static InterruptPolicy fromId(@Nullable String id) {
            for (InterruptPolicy policy : values()) if (policy.id.equalsIgnoreCase(id)) return policy;
            return USE_TASK_DEFAULT;
        }
    }
}
