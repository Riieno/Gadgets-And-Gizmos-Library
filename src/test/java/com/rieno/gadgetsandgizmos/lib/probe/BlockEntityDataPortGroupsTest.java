package com.rieno.gadgetsandgizmos.lib.probe;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BlockEntityDataPortGroupsTest {
    @Test
    void bearingModeStaysOutsideRotationGroup() {
        Map<String, String> ports = new LinkedHashMap<>();
        ports.put("angle_mode", "string");
        ports.put("control_mode", "string");
        ports.put("pivot_angle", "number");
        ports.put("min_angle", "number");
        ports.put("max_angle", "number");

        Map<String, Map<String, String>> groups = BlockEntityDataPortGroups.group(ports);

        assertEquals(Map.of("pivot_angle", "number", "min_angle", "number", "max_angle", "number"),
                groups.get("rotation"));
        assertFalse(groups.values().stream().anyMatch(group -> group.containsKey("angle_mode")));
    }
}
