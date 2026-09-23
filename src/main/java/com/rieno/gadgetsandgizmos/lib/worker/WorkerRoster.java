package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Persist controller ownership of workers while allowing stations to host their live runtime
public final class WorkerRoster {
    private final Map<UUID, UUID> stationByWorker = new LinkedHashMap<>();

    // Assign one worker to a station owned by this controller
    public void assign(UUID workerId, UUID stationId) {
        if (workerId == null || stationId == null) return;
        stationByWorker.put(workerId, stationId);
    }

    // Remove one worker only when the expected station still owns it
    public void release(UUID workerId, UUID stationId) {
        if (workerId == null || stationId == null) return;
        stationByWorker.remove(workerId, stationId);
    }

    // Check whether one controller-owned worker is hosted by a station
    public boolean owns(UUID workerId, UUID stationId) {
        return workerId != null && stationId != null && stationId.equals(stationByWorker.get(workerId));
    }

    // Get the station currently hosting one controller-owned worker
    public UUID station(UUID workerId) {
        return workerId == null ? null : stationByWorker.get(workerId);
    }

    // Get all workers currently hosted by one station
    public Set<UUID> workersAt(UUID stationId) {
        if (stationId == null) return Set.of();
        return stationByWorker.entrySet().stream()
                .filter(entry -> stationId.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    // Serialize controller-owned worker assignments
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        ListTag assignments = new ListTag();
        stationByWorker.forEach((workerId, stationId) -> {
            CompoundTag row = new CompoundTag();
            row.putUUID("Worker", workerId);
            row.putUUID("Station", stationId);
            assignments.add(row);
        });
        tag.put("Assignments", assignments);
        return tag;
    }

    // Restore controller-owned worker assignments
    public void fromTag(CompoundTag tag) {
        stationByWorker.clear();
        if (tag == null) return;
        ListTag assignments = tag.getList("Assignments", Tag.TAG_COMPOUND);
        for (int index = 0; index < assignments.size(); index++) {
            CompoundTag row = assignments.getCompound(index);
            if (row.hasUUID("Worker") && row.hasUUID("Station")) {
                assign(row.getUUID("Worker"), row.getUUID("Station"));
            }
        }
    }
}
