package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.nbt.CompoundTag;

// Preserve a structured worker action failure for graph and UI integrations
public record WorkerFailureReason(String code, String message) {
    // Initialize one failure reason
    public WorkerFailureReason {
        code = code == null || code.isBlank() ? "unknown" : code.strip();
        message = message == null ? "" : message.strip();
    }

    // Create an empty failure reason
    public static WorkerFailureReason none() {
        return new WorkerFailureReason("none", "");
    }

    // Check whether an action failed
    public boolean failed() {
        return !"none".equals(code);
    }

    // Write the failure reason
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Code", code);
        tag.putString("Message", message);
        return tag;
    }

    // Read the failure reason
    public static WorkerFailureReason fromTag(CompoundTag tag) {
        return new WorkerFailureReason(tag == null ? "unknown" : tag.getString("Code"),
                tag == null ? "" : tag.getString("Message"));
    }
}
