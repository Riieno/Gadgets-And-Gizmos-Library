package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashSet;
import java.util.Set;

// Describe reusable resource matching rules without requiring a particular registry or UI
public record WorkerResourceFilter(WorkerResourceType type, Mode mode, Set<ResourceLocation> resources,
                                   Set<ResourceLocation> tags, boolean matchComponents, boolean ignoreDamage,
                                   String namespace) {
    // Initialize one resource filter
    public WorkerResourceFilter {
        type = type == null ? WorkerResourceType.ITEM : type;
        mode = mode == null ? Mode.ALLOW_LIST : mode;
        resources = resources == null ? Set.of() : Set.copyOf(resources);
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        namespace = namespace == null ? "" : namespace.strip().toLowerCase(java.util.Locale.ROOT);
    }

    // Check whether a resource id is explicitly allowed by this filter's direct rules
    public boolean matchesId(ResourceLocation id) {
        if (id == null) return false;
        boolean directMatch = resources.isEmpty() || resources.contains(id);
        boolean namespaceMatch = namespace.isBlank() || namespace.equals(id.getNamespace());
        boolean matched = directMatch && namespaceMatch;
        return mode == Mode.ALLOW_LIST ? matched : !matched;
    }

    // Write the filter
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", type.id());
        tag.putString("Mode", mode.id());
        tag.put("Resources", writeIds(resources));
        tag.put("Tags", writeIds(tags));
        tag.putBoolean("MatchComponents", matchComponents);
        tag.putBoolean("IgnoreDamage", ignoreDamage);
        tag.putString("Namespace", namespace);
        return tag;
    }

    // Read the filter
    public static WorkerResourceFilter fromTag(CompoundTag tag) {
        CompoundTag safe = tag == null ? new CompoundTag() : tag;
        return new WorkerResourceFilter(WorkerResourceType.fromId(safe.getString("Type")),
                Mode.fromId(safe.getString("Mode")), readIds(safe.getList("Resources", Tag.TAG_STRING)),
                readIds(safe.getList("Tags", Tag.TAG_STRING)), safe.getBoolean("MatchComponents"),
                safe.getBoolean("IgnoreDamage"), safe.getString("Namespace"));
    }

    // Write resource or tag ids
    private static ListTag writeIds(Set<ResourceLocation> ids) {
        ListTag values = new ListTag();
        ids.stream().sorted(java.util.Comparator.comparing(ResourceLocation::toString))
                .forEach(id -> values.add(StringTag.valueOf(id.toString())));
        return values;
    }

    // Read resource or tag ids
    private static Set<ResourceLocation> readIds(ListTag values) {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (int index = 0; index < values.size(); index++) {
            ResourceLocation id = ResourceLocation.tryParse(values.getString(index));
            if (id != null) ids.add(id);
        }
        return Set.copyOf(ids);
    }

    public enum Mode {
        ALLOW_LIST("allow_list"),
        DENY_LIST("deny_list");

        private final String id;

        // Initialize the filter mode
        Mode(String id) {
            this.id = id;
        }

        // Get the stable serialized id
        public String id() {
            return id;
        }

        // Resolve one filter mode from its serialized id
        public static Mode fromId(String id) {
            for (Mode mode : values()) if (mode.id.equalsIgnoreCase(id)) return mode;
            return ALLOW_LIST;
        }
    }
}
