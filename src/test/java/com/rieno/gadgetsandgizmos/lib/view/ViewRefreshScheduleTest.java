package com.rieno.gadgetsandgizmos.lib.view;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

// Keep multiple feeds at the requested cadence even when captures are expensive
class ViewRefreshScheduleTest{
    // Each feed receives ten updates rather than splitting a global allowance
    @Test
    void everyVisibleFeedKeepsItsOwnRate(){
        var feeds = new ViewRefreshSchedule[]{new ViewRefreshSchedule(), new ViewRefreshSchedule(),
                new ViewRefreshSchedule(), new ViewRefreshSchedule()};
        int[] counts = new int[feeds.length];
        for(long now = 0; now < 1_000_000_000L; now += 16_666_667L){
            for(int idx = 0; idx < feeds.length; idx++){
                if(!feeds[idx].ready(now, 10)) continue;
                feeds[idx].captured(now);
                counts[idx]++;
            }
        }
        for(int val : counts) assertEquals(10, val);
    }
    // Long frames skip old deadlines and a config change takes effect immediately
    @Test
    void changesRateWithoutQueuingMissedFrames(){
        var feed = new ViewRefreshSchedule();
        assertTrue(feed.ready(0, 10));
        feed.captured(0);
        assertFalse(feed.ready(99_999_999L, 10));
        assertTrue(feed.ready(400_000_000L, 10));
        feed.captured(400_000_000L);
        assertFalse(feed.ready(400_000_001L, 10));
        assertTrue(feed.ready(400_000_001L, 20));
        feed.captured(400_000_001L);
        assertTrue(feed.ready(450_000_001L, 20));
    }
}
