package com.rieno.gadgetsandgizmos.lib.graph;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Compose stable graph ports and labelled sections for one or more data targets
public final class GraphTargetPortLayout {
    /*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        PRELOAD / SETUP
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the target port layout helpers
    private GraphTargetPortLayout() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                           FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Build a stable port layout for the supplied targets
    public static Layout compose(Collection<Target> targets, boolean mergeLikePorts) {
        List<Target> values = targets == null ? List.of() : targets.stream()
                .filter(target -> target != null && !target.id().isBlank())
                .toList();
        Map<String, String> ports = new LinkedHashMap<>();
        Map<String, String> labels = new LinkedHashMap<>();
        Map<String, List<Binding>> bindings = new LinkedHashMap<>();
        List<Section> sections = new ArrayList<>();
        Map<String, String> merged = new LinkedHashMap<>();
        Map<String, List<String>> sectionPorts = new LinkedHashMap<>();

        for (Target target : values) {
            String targetKey = key(target.id());
            String sectionId = "target_" + targetKey;
            List<String> entries = new ArrayList<>();
            for (Port port : target.ports()) {
                if (port == null || port.id().isBlank() || port.type().isBlank()) {
                    continue;
                }
                String label = port.label().isBlank() ? port.id() : port.label();
                String portId;
                if (mergeLikePorts) {
                    String mergeKey = normalize(label);
                    portId = merged.computeIfAbsent(mergeKey, ignored -> "merged_" + key(label));
                } else {
                    portId = sectionId + "__" + key(port.id());
                }
                ports.putIfAbsent(portId, port.type());
                labels.putIfAbsent(portId, label);
                bindings.computeIfAbsent(portId, ignored -> new ArrayList<>())
                        .add(new Binding(target.id(), port.id()));
                entries.add(portId);
            }
            sectionPorts.put(sectionId, entries);
            if (!mergeLikePorts) {
                sections.add(new Section(sectionId, target.label().isBlank() ? target.id() : target.label(), entries));
            }
        }
        if (mergeLikePorts && !ports.isEmpty()) {
            sections.add(new Section("merged", "Merged Ports", new ArrayList<>(ports.keySet())));
        }
        return new Layout(ports, labels, bindings, sections);
    }

    // Store one selectable graph target schema
    public record Target(String id, String label, Collection<Port> ports) {
        // Normalize one target schema
        public Target {
            id = id == null ? "" : id.strip();
            label = label == null ? "" : label.strip();
            ports = List.copyOf(ports == null ? List.of() : ports);
        }
    }

    // Store one target data port
    public record Port(String id, String label, String type) {
        // Normalize one target port
        public Port {
            id = id == null ? "" : id.strip();
            label = label == null ? "" : label.strip();
            type = type == null ? "" : type.strip();
        }
    }

    // Map one composed graph port back to a target data port
    public record Binding(String targetId, String portId) {
        // Normalize one binding
        public Binding {
            targetId = targetId == null ? "" : targetId.strip();
            portId = portId == null ? "" : portId.strip();
        }
    }

    // Store one labelled collapsible port section
    public record Section(String id, String label, List<String> ports) {
        // Normalize one section
        public Section {
            id = id == null ? "" : id.strip();
            label = label == null ? "" : label.strip();
            ports = List.copyOf(ports == null ? List.of() : ports);
        }
    }

    // Expose the composed target ports without implementation-specific state
    public record Layout(Map<String, String> ports, Map<String, String> labels,
                         Map<String, List<Binding>> bindings, List<Section> sections) {
        // Make every returned collection stable and read-only
        public Layout {
            ports = Collections.unmodifiableMap(new LinkedHashMap<>(ports == null ? Map.of() : ports));
            labels = Collections.unmodifiableMap(new LinkedHashMap<>(labels == null ? Map.of() : labels));
            Map<String, List<Binding>> copiedBindings = new LinkedHashMap<>();
            if (bindings != null) {
                bindings.forEach((port, entries) -> copiedBindings.put(port,
                        List.copyOf(entries == null ? List.of() : entries)));
            }
            bindings = Collections.unmodifiableMap(copiedBindings);
            sections = List.copyOf(sections == null ? List.of() : sections);
        }
    }

    // Convert one persisted id into a safe stable graph-port fragment
    private static String key(String value) {
        String normalized = normalize(value);
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            if (character >= 'a' && character <= 'z' || character >= '0' && character <= '9') {
                result.append(character);
            } else {
                result.append('_').append(Integer.toHexString(character));
            }
        }
        return result.isEmpty() ? "port" : result.toString();
    }

    // Normalize one user-facing value for stable matching
    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}
