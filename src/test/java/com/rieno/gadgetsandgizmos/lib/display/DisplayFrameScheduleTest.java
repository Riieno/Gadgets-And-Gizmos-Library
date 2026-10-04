package com.rieno.gadgetsandgizmos.lib.display;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisplayFrameScheduleTest {
    @Test
    void staticContentReusesItsTextureUntilChanged() {
        assertTrue(DisplayFrameSchedule.shouldRender(false, false, false, false, 1000, 100, 50));
        assertTrue(DisplayFrameSchedule.shouldRender(true, true, false, false, 1000, 100, 50));
        assertTrue(DisplayFrameSchedule.shouldRender(true, false, true, false, 1000, 100, 50));
        assertFalse(DisplayFrameSchedule.shouldRender(true, false, false, false, 1000, 100, 50));
        assertFalse(DisplayFrameSchedule.shouldRender(true, false, false, true, 149, 100, 50));
        assertTrue(DisplayFrameSchedule.shouldRender(true, false, false, true, 150, 100, 50));
    }
}
