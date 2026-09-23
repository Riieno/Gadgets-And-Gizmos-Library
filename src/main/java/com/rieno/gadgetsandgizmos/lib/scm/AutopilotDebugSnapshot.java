package com.rieno.gadgetsandgizmos.lib.scm;

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Store one detached, renderer-independent autopilot diagnostic snapshot. */
public record AutopilotDebugSnapshot(
        UUID vehicleId,
        String vehicleName,
        Vec3 anchor,
        State state,
        long gameTime,
        List<Section> sections
) {
    /** Normalize one immutable diagnostic snapshot. */
    public AutopilotDebugSnapshot {
        vehicleId = vehicleId == null ? new UUID(0L, 0L) : vehicleId;
        vehicleName = clean(vehicleName, "Unnamed vehicle");
        anchor = finite(anchor);
        state = state == null ? State.IDLE : state;
        sections = sections == null ? List.of() : sections.stream()
                .filter(section -> section != null && !section.entries().isEmpty())
                .toList();
    }

    /** Convert the complete snapshot into a portable nested graph value. */
    public GraphValue graphValue() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("vehicle_id", vehicleId.toString());
        values.put("vehicle_name", vehicleName);
        values.put("state", state.name());
        values.put("game_time", gameTime);
        values.put("anchor", Map.of(
                "x", anchor.x,
                "y", anchor.y,
                "z", anchor.z));
        List<Map<String, Object>> sectionValues = new ArrayList<>(sections.size());
        for (Section section : sections) {
            List<Map<String, Object>> entryValues = new ArrayList<>(section.entries().size());
            for (Entry entry : section.entries()) {
                entryValues.add(Map.of(
                        "label", entry.label(),
                        "value", entry.value(),
                        "tone", entry.tone().name()));
            }
            sectionValues.add(Map.of(
                    "name", section.name(),
                    "entries", List.copyOf(entryValues)));
        }
        values.put("sections", List.copyOf(sectionValues));
        return GraphValue.map(values);
    }

    /** Store one named group of live brain values. */
    public record Section(String name, List<Entry> entries) {
        /** Normalize one immutable section. */
        public Section {
            name = clean(name, "State");
            entries = entries == null ? List.of() : entries.stream()
                    .filter(entry -> entry != null)
                    .toList();
        }
    }

    /** Store one labelled value and its presentation tone. */
    public record Entry(String label, String value, Tone tone) {
        /** Create one normal diagnostic value. */
        public Entry(String label, String value) {
            this(label, value, Tone.NORMAL);
        }

        /** Normalize one immutable entry. */
        public Entry {
            label = clean(label, "Value");
            value = clean(value, "-");
            tone = tone == null ? Tone.NORMAL : tone;
        }
    }

    /** Describe the primary behavior currently owning the vehicle. */
    public enum State {
        IDLE,
        NAVIGATING,
        FOLLOWING_ROUTE,
        PLANNING,
        AVOIDING,
        YIELDING,
        RECOVERING,
        DOCKING,
        BLOCKED
    }

    /** Describe the diagnostic significance of one value. */
    public enum Tone {
        NORMAL,
        MUTED,
        ACCENT,
        POSITIVE,
        WARNING,
        DANGER
    }

    // Remove control characters from caller-owned labels without imposing a transport limit.
    private static String clean(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String clean = value.codePoints()
                .filter(codePoint -> !Character.isISOControl(codePoint))
                .collect(StringBuilder::new, StringBuilder::appendCodePoint,
                        StringBuilder::append)
                .toString().strip();
        return clean.isBlank() ? fallback : clean;
    }

    // Normalize one world position.
    private static Vec3 finite(Vec3 value) {
        return value != null && Double.isFinite(value.x)
                && Double.isFinite(value.y) && Double.isFinite(value.z)
                ? value : Vec3.ZERO;
    }
}
