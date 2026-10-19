package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

// Describe deferred worker source and destination selection without owning a controller implementation
public record WorkerContainerSelector(Mode mode, @Nullable UUID endpointId, Preference preference,
                                      int searchRange) {
    // Initialize one deferred container selector
    public WorkerContainerSelector {
        mode = mode == null ? Mode.AUTOMATIC : mode;
        preference = preference == null ? Preference.ANY : preference;
        if (mode == Mode.AUTOMATIC) endpointId = null;
        searchRange = Math.max(0, searchRange);
    }

    // Create an automatic container selector
    public static WorkerContainerSelector automatic(Preference preference, int searchRange) {
        return new WorkerContainerSelector(Mode.AUTOMATIC, null, preference, searchRange);
    }

    // Create a selector for one linked endpoint
    public static WorkerContainerSelector specific(UUID endpointId) {
        return new WorkerContainerSelector(Mode.SPECIFIC, endpointId, Preference.ANY, 0);
    }

    // Check whether this selector will resolve through its host index
    public boolean automatic() {
        return mode == Mode.AUTOMATIC;
    }

    // Write the selector
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Mode", mode.id());
        if (endpointId != null) tag.putUUID("Endpoint", endpointId);
        tag.putString("Preference", preference.id());
        tag.putInt("SearchRange", searchRange);
        return tag;
    }

    // Read the selector
    public static WorkerContainerSelector fromTag(CompoundTag tag) {
        CompoundTag safe = tag == null ? new CompoundTag() : tag;
        return new WorkerContainerSelector(Mode.fromId(safe.getString("Mode")),
                safe.hasUUID("Endpoint") ? safe.getUUID("Endpoint") : null,
                Preference.fromId(safe.getString("Preference")), safe.getInt("SearchRange"));
    }

    public enum Mode {
        AUTOMATIC("automatic"),
        SPECIFIC("specific");

        private final String id;

        // Initialize the selector mode
        Mode(String id) {
            this.id = id;
        }

        // Get the stable serialized id
        public String id() {
            return id;
        }

        // Resolve a selector mode from its serialized id
        public static Mode fromId(String id) {
            for (Mode mode : values()) if (mode.id.equalsIgnoreCase(id)) return mode;
            return AUTOMATIC;
        }
    }

    public enum Preference {
        ANY("any"),
        NEAREST("nearest"),
        HIGHEST_STOCK("highest_stock"),
        MOST_SPACE("most_space"),
        PRIORITY("priority");

        private final String id;

        // Initialize the selector preference
        Preference(String id) {
            this.id = id;
        }

        // Get the stable serialized id
        public String id() {
            return id;
        }

        // Resolve a selector preference from its serialized id
        public static Preference fromId(String id) {
            for (Preference preference : values()) if (preference.id.equalsIgnoreCase(id)) return preference;
            return ANY;
        }
    }
}
