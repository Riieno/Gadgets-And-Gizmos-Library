package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

// Expose a read-only endpoint and resource summary to worker configuration clients
public record WorkerEndpointSnapshot(
        UUID id,
        @Nullable UUID subLevelId,
        BlockPos position,
        String label,
        String blockId,
        boolean extractionAllowed,
        boolean insertionAllowed,
        List<ResourceAmount> resources
) {
    // Initialize the endpoint snapshot
    public WorkerEndpointSnapshot {
        id = id == null ? UUID.randomUUID() : id;
        position = position == null ? BlockPos.ZERO : position.immutable();
        blockId = blockId == null ? "" : blockId.strip();
        label = label == null || label.isBlank() ? blockId : label.strip();
        resources = resources == null ? List.of() : List.copyOf(resources);
    }

    // Write the endpoint snapshot
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        if (subLevelId != null) tag.putUUID("SubLevel", subLevelId);
        tag.putLong("Position", position.asLong());
        tag.putString("Label", label);
        tag.putString("BlockId", blockId);
        tag.putBoolean("Extract", extractionAllowed);
        tag.putBoolean("Insert", insertionAllowed);
        ListTag amounts = new ListTag();
        for (ResourceAmount resource : resources) amounts.add(resource.toTag());
        tag.put("Resources", amounts);
        return tag;
    }

    // Read the endpoint snapshot
    public static WorkerEndpointSnapshot fromTag(CompoundTag tag) {
        CompoundTag safe = tag == null ? new CompoundTag() : tag;
        ListTag amounts = safe.getList("Resources", Tag.TAG_COMPOUND);
        List<ResourceAmount> resources = amounts.stream()
                .map(value -> ResourceAmount.fromTag((CompoundTag) value)).toList();
        return new WorkerEndpointSnapshot(
                safe.hasUUID("Id") ? safe.getUUID("Id") : UUID.randomUUID(),
                safe.hasUUID("SubLevel") ? safe.getUUID("SubLevel") : null,
                BlockPos.of(safe.getLong("Position")), safe.getString("Label"),
                safe.getString("BlockId"), safe.getBoolean("Extract"),
                safe.getBoolean("Insert"), resources);
    }

    // Describe one resource amount and endpoint capacity
    public record ResourceAmount(WorkerResourceKey resource, long amount, long capacity) {
        // Initialize the resource amount
        public ResourceAmount {
            resource = resource == null ? WorkerResourceKey.energy() : resource;
            amount = Math.max(0L, amount);
            capacity = Math.max(amount, capacity);
        }

        // Write the resource amount
        private CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.put("Resource", resource.toTag());
            tag.putLong("Amount", amount);
            tag.putLong("Capacity", capacity);
            return tag;
        }

        // Read the resource amount
        private static ResourceAmount fromTag(CompoundTag tag) {
            return new ResourceAmount(WorkerResourceKey.fromTag(tag.getCompound("Resource")),
                    tag.getLong("Amount"), tag.getLong("Capacity"));
        }
    }
}
