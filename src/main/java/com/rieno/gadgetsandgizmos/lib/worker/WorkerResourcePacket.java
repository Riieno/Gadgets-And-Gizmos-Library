package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.nbt.CompoundTag;

// Carry a serialized resource payload between storage adapters
public record WorkerResourcePacket(WorkerResourceKey resource, long amount, CompoundTag payload) {
    // Initialize the worker resource packet
    public WorkerResourcePacket {
        resource = resource == null ? WorkerResourceKey.energy() : resource;
        amount = Math.max(0L, amount);
        payload = payload == null ? new CompoundTag() : payload.copy();
    }

    // Create an empty packet
    public static WorkerResourcePacket empty(WorkerResourceKey resource) {
        return new WorkerResourcePacket(resource, 0L, new CompoundTag());
    }

    // Check if the packet has a transferable amount
    public boolean isEmpty() {
        return amount <= 0L;
    }

    // Copy the packet with a bounded amount
    public WorkerResourcePacket withAmount(long nextAmount) {
        return new WorkerResourcePacket(resource, Math.min(amount, Math.max(0L, nextAmount)), payload);
    }

    // Keep the unaccepted portion after a bounded insertion attempt
    public WorkerResourcePacket remainderAfter(long acceptedAmount) {
        long accepted = Math.max(0L, Math.min(amount, acceptedAmount));
        return new WorkerResourcePacket(resource, amount - accepted, payload);
    }

    // Serialize this packet for persistent worker custody
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.put("Resource", resource.toTag());
        tag.putLong("Amount", amount);
        if (!payload.isEmpty()) tag.put("Payload", payload.copy());
        return tag;
    }

    // Deserialize one persistent worker packet
    public static WorkerResourcePacket fromTag(CompoundTag tag) {
        if (tag == null || !tag.contains("Resource")) return empty(WorkerResourceKey.energy());
        return new WorkerResourcePacket(WorkerResourceKey.fromTag(tag.getCompound("Resource")),
                tag.getLong("Amount"), tag.getCompound("Payload"));
    }
}
