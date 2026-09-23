package com.rieno.gadgetsandgizmos.lib.shipping;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScheduleConditionGroupsTest {
    @Test
    void switchesBetweenAndAndOrWithoutLosingOrder(){
        List<List<String>> groups = new ArrayList<>();
        groups.add(new ArrayList<>(List.of("a", "b", "c")));
        assertEquals(new ScheduleConditionGroups.Position(1, 0),
                ScheduleConditionGroups.setConjunction(groups, 0, 1, false));
        assertEquals(List.of(List.of("a"), List.of("b", "c")), groups);
        assertEquals(new ScheduleConditionGroups.Position(0, 1),
                ScheduleConditionGroups.setConjunction(groups, 1, 0, true));
        assertEquals(List.of(List.of("a", "b", "c")), groups);
    }
}
