package com.rieno.gadgetsandgizmos.lib.view;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ViewFeedResolutionTest{
    @Test void selectedResolutionScalesBothSmallAndLargeViewports(){
        assertEquals(new ViewFeedResolution.Size(640, 360), ViewFeedResolution.fit(160, 90, 640));
        assertEquals(new ViewFeedResolution.Size(320, 180), ViewFeedResolution.fit(1920, 1080, 320));
        assertEquals(new ViewFeedResolution.Size(180, 320), ViewFeedResolution.fit(1080, 1920, 320));
        assertEquals(new ViewFeedResolution.Size(64, 36), ViewFeedResolution.fit(160, 90, 64));
    }

    @Test void invalidSettingsRemainWithinFramebufferLimits(){
        assertEquals(new ViewFeedResolution.Size(64, 64), ViewFeedResolution.fit(0, 0, -1));
        assertEquals(new ViewFeedResolution.Size(2048, 2048), ViewFeedResolution.fit(1, 1, Integer.MAX_VALUE));
        assertEquals(new ViewFeedResolution.Size(2048, 16), ViewFeedResolution.fit(Integer.MAX_VALUE, 1, 2048));
    }

    @Test void refreshRateChangesApplyWithoutWaitingForTheOldDeadline(){
        var schedule = new ViewRefreshSchedule();
        assertTrue(schedule.ready(0, 1));
        schedule.captured(0);
        assertFalse(schedule.ready(100_000_000, 1));
        assertTrue(schedule.ready(100_000_000, 60));
        schedule.captured(100_000_000);
        assertFalse(schedule.ready(110_000_000, 60));
        assertTrue(schedule.ready(117_000_000, 60));
        assertTrue(schedule.ready(117_000_000, 1));
    }
}
