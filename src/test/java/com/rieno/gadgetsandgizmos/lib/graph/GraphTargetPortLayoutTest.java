package com.rieno.gadgetsandgizmos.lib.graph;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GraphTargetPortLayoutTest {
    @Test
    void composedPortsRetainTargetOptions() {
        GraphTargetPortLayout.Layout layout = GraphTargetPortLayout.compose(List.of(
                new GraphTargetPortLayout.Target("thruster", "Thruster", List.of(
                        new GraphTargetPortLayout.Port("control_mode", "Control Mode", "string",
                                List.of("auto", "redstone", "computer"))))), false);

        String port = layout.ports().keySet().iterator().next();
        assertEquals(List.of("auto", "redstone", "computer"), layout.options().get(port));
        assertEquals("control_mode", layout.bindings().get(port).getFirst().portId());
    }

    @Test
    void mergedPortsOnlyOfferValuesSharedByEveryTarget() {
        GraphTargetPortLayout.Layout layout = GraphTargetPortLayout.compose(List.of(
                new GraphTargetPortLayout.Target("first", "First", List.of(
                        new GraphTargetPortLayout.Port("control_mode", "Control Mode", "string",
                                List.of("auto", "redstone", "computer")))),
                new GraphTargetPortLayout.Target("second", "Second", List.of(
                        new GraphTargetPortLayout.Port("control_mode", "Control Mode", "string",
                                List.of("auto", "computer", "servo"))))), true);

        String port = layout.ports().keySet().iterator().next();
        assertEquals(List.of("auto", "computer"), layout.options().get(port));
        assertFalse(layout.options().isEmpty());
    }
}
