package com.rieno.gadgetsandgizmos.lib.graph;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class GraphTargetPortLayoutIndependentTest{
    @Test
    void independentControlsRemainInTheirTargetsWhileOtherPortsMerge(){
        var fields = List.of(new GraphTargetPortLayout.Port("speed", "Speed", "number"),
                new GraphTargetPortLayout.Port("signal", "Signal", "number"));
        var targets = List.of(new GraphTargetPortLayout.Target("left", "Left", fields),
                new GraphTargetPortLayout.Target("right", "Right", fields));
        var result = GraphTargetPortLayout.compose(targets, true, Map.of(), Set.of("signal"));
        assertEquals(3, result.ports().size());
        assertEquals(2, result.bindings().get("merged_speed").size());
        assertEquals(List.of("target_left__signal"), result.sections().getFirst().ports());
        assertEquals(List.of("target_right__signal"), result.sections().get(1).ports());
        assertEquals(List.of("merged_speed"), result.sections().getLast().ports());
        assertEquals(2, GraphTargetPortLayout.compose(targets, true).ports().size());
        assertEquals(1, GraphTargetPortLayout.compose(targets, true).sections().size());
        var moved = GraphTargetPortLayout.compose(List.of(new GraphTargetPortLayout.Target("moved", "Moved", fields)),
                true, Map.of("moved", "left"), Set.of("signal"));
        assertTrue(moved.ports().containsKey("target_left__signal"));
    }
}
