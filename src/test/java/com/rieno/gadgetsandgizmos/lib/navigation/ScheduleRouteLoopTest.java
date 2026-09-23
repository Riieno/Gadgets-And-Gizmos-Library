package com.rieno.gadgetsandgizmos.lib.navigation;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScheduleRouteLoopTest {
    @Test
    void cyclicSelectionProducesOneShortestOrderedLoop() {
        List<ScheduleRouteLoop.Candidate<String>> selected = ScheduleRouteLoop.select(List.of(
                List.of(candidate("a-near", 0.0D), candidate("a-far", 100.0D)),
                List.of(candidate("b-near", 10.0D), candidate("b-far", 110.0D)),
                List.of(candidate("c-near", 20.0D), candidate("c-far", 120.0D))
        ), true);

        assertEquals(List.of("a-near", "b-near", "c-near"),
                selected.stream().map(ScheduleRouteLoop.Candidate::value).toList());
    }

    @Test
    void openSelectionRetainsEveryScheduleLayerExactlyOnce() {
        List<ScheduleRouteLoop.Candidate<String>> selected = ScheduleRouteLoop.select(List.of(
                List.of(candidate("a", 0.0D)),
                List.of(candidate("b", 5.0D), candidate("detour", 50.0D)),
                List.of(candidate("c", 10.0D))
        ), false);

        assertEquals(List.of("a", "b", "c"),
                selected.stream().map(ScheduleRouteLoop.Candidate::value).toList());
    }

    private static ScheduleRouteLoop.Candidate<String> candidate(String name, double x) {
        return new ScheduleRouteLoop.Candidate<>(name, new Vec3(x, 0.0D, 0.0D));
    }
}
