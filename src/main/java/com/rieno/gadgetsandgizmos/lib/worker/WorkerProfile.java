package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.nbt.CompoundTag;

import java.util.Locale;
import java.util.UUID;

// Store reusable worker identity and job preferences independently of an entity implementation
public record WorkerProfile(UUID workerId, String name, Job job, boolean enabled) {
    // Initialize the worker profile
    public WorkerProfile {
        workerId = workerId == null ? UUID.randomUUID() : workerId;
        name = name == null || name.isBlank() ? "Worker" : name.strip();
        if (name.length() > 64) name = name.substring(0, 64);
        job = job == null ? Job.ANY : job;
    }

    // Write the worker profile
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("WorkerId", workerId);
        tag.putString("Name", name);
        tag.putString("Job", job.id());
        tag.putBoolean("Enabled", enabled);
        return tag;
    }

    // Read the worker profile
    public static WorkerProfile fromTag(CompoundTag tag) {
        return new WorkerProfile(
                tag != null && tag.hasUUID("WorkerId") ? tag.getUUID("WorkerId") : UUID.randomUUID(),
                tag == null ? "" : tag.getString("Name"),
                Job.fromId(tag == null ? "" : tag.getString("Job")),
                tag != null && tag.getBoolean("Enabled"));
    }

    // Define the supported worker jobs
    public enum Job {
        ANY("any"),
        ITEMS("items"),
        FLUIDS("fluids"),
        ENERGY("energy"),
        FUEL("fuel");

        private final String id;

        Job(String id) {
            this.id = id;
        }

        // Get the stable job id
        public String id() {
            return id;
        }

        // Check whether this job accepts the resource type
        public boolean accepts(WorkerResourceType type) {
            return this == ANY || switch (type == null ? WorkerResourceType.ITEM : type) {
                case ITEM -> this == ITEMS;
                case FLUID -> this == FLUIDS;
                case ENERGY -> this == ENERGY;
                case FUEL -> this == FUEL;
            };
        }

        // Resolve a job id
        public static Job fromId(String id) {
            String normalized = id == null ? "" : id.strip().toLowerCase(Locale.ROOT);
            for (Job job : values()) {
                if (job.id.equals(normalized)) return job;
            }
            return ANY;
        }
    }
}
