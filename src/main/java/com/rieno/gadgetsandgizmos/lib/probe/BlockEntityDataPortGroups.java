package com.rieno.gadgetsandgizmos.lib.probe;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// Build stable MAP groups for related block entity data ports
public final class BlockEntityDataPortGroups {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Set<String> ROTATION_TERMS = Set.of(
            "angle", "tilt", "rotation", "yaw", "pitch", "roll", "heading", "azimuth");
    private static final Set<String> MOVEMENT_PORTS = Set.of(
            "forward", "backward", "left", "right", "up", "down", "axis_x", "axis_y", "axis_z",
            "magnitude", "active");

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the block entity data port groups
    private BlockEntityDataPortGroups() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Group related data ports without changing their underlying identifiers
    public static Map<String, Map<String, String>> group(Map<String, String> ports) {
        if (ports == null || ports.isEmpty()) {
            return Map.of();
        }

        Map<String, String> available = new LinkedHashMap<>();
        ports.forEach((id, type) -> {
            if (id != null && !id.isBlank() && type != null && !type.isBlank()) {
                available.put(id, type);
            }
        });
        if (available.isEmpty()) {
            return Map.of();
        }

        Map<String, Map<String, String>> groups = new LinkedHashMap<>();
        Set<String> claimed = new LinkedHashSet<>();
        addGroup(groups, claimed, available, "position", List.of("x", "y", "z"));
        addGroup(groups, claimed, available, "inventory", List.of(
                "items", "item_count", "item_capacity", "item_fill"));
        addGroup(groups, claimed, available, "fluids", List.of(
                "fluids", "fluid_amount", "fluid_capacity", "fluid_fill"));
        addGroup(groups, claimed, available, "energy_status", List.of(
                "energy", "energy_capacity", "energy_fill"));
        addGroup(groups, claimed, available, "movement", MOVEMENT_PORTS);
        addGroup(groups, claimed, available, "rotation", matchingPorts(
                available, claimed, BlockEntityDataPortGroups::isRotationPort));

        Map<String, List<String>> prefixed = new LinkedHashMap<>();
        for (String id : available.keySet()) {
            if (claimed.contains(id)) {
                continue;
            }
            int split = id.indexOf('_');
            if (split <= 0 || split >= id.length() - 1) {
                continue;
            }
            prefixed.computeIfAbsent(id.substring(0, split), ignored -> new ArrayList<>()).add(id);
        }
        prefixed.forEach((prefix, ids) -> addGroup(groups, claimed, available, prefix, ids));

        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        groups.forEach((id, entries) -> result.put(id,
                Collections.unmodifiableMap(new LinkedHashMap<>(entries))));
        return Collections.unmodifiableMap(result);
    }

    // Add one group when at least two ports belong to it
    private static void addGroup(Map<String, Map<String, String>> groups, Set<String> claimed,
                                 Map<String, String> available, String preferredId,
                                 Collection<String> candidates) {
        Map<String, String> entries = new LinkedHashMap<>();
        for (String id : candidates) {
            if (!claimed.contains(id) && available.containsKey(id)) {
                entries.put(id, available.get(id));
            }
        }
        if (entries.size() < 2) {
            return;
        }

        String id = groupId(preferredId, available, groups);
        groups.put(id, entries);
        claimed.addAll(entries.keySet());
    }

    // Find available ports which match one semantic category
    private static List<String> matchingPorts(Map<String, String> available, Set<String> claimed,
                                              java.util.function.Predicate<String> predicate) {
        return available.keySet().stream()
                .filter(id -> !claimed.contains(id))
                .filter(predicate)
                .toList();
    }

    // Check if a port describes a rotation component
    private static boolean isRotationPort(String id) {
        String normalized = id == null ? "" : id.toLowerCase(Locale.ROOT);
        return ROTATION_TERMS.stream().anyMatch(normalized::contains);
    }

    // Choose a MAP port id which cannot replace an existing data port
    private static String groupId(String preferred, Map<String, String> ports,
                                  Map<String, Map<String, String>> groups) {
        String base = preferred == null || preferred.isBlank() ? "data" : preferred;
        String id = base;
        int suffix = 2;
        while (ports.containsKey(id) || groups.containsKey(id)) {
            id = base + "_data";
            if (!ports.containsKey(id) && !groups.containsKey(id)) {
                break;
            }
            id = base + "_data_" + suffix++;
        }
        return id;
    }
}
