package com.rieno.gadgetsandgizmos.lib.plot;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotPointTimelineTest {
    @Test
    void xModeSelectorUsesTheThreeSupportedChoices() {
        assertEquals(List.of("ignore", "time_offset", "absolute"),
                PlotPointTimeline.XMode.optionIds());
        assertEquals(PlotPointTimeline.XMode.IGNORE, PlotPointTimeline.XMode.fromId(null));
        assertEquals(PlotPointTimeline.XMode.TIME_OFFSET,
                PlotPointTimeline.XMode.fromId("time_offset"));
        assertEquals(PlotPointTimeline.XMode.ABSOLUTE,
                PlotPointTimeline.XMode.fromId("absolute"));
    }

    @Test
    void switchingModesKeepsIgnoreUnshiftedAndCapturesFirstTimeOffset() {
        PlotPointTimeline timeline = new PlotPointTimeline();
        timeline.add("A", 0, 1, 0xFFFFFFFF, 100, PlotPointTimeline.XMode.IGNORE);
        timeline.add("A", 8, 2, 0xFFFFFFFF, 110, PlotPointTimeline.XMode.TIME_OFFSET);
        timeline.add("A", 0, 3, 0xFFFFFFFF, 120, PlotPointTimeline.XMode.IGNORE);
        timeline.add("A", 20, 4, 0xFFFFFFFF, 130, PlotPointTimeline.XMode.TIME_OFFSET);

        assertEquals(0.0D, timeline.samples().get(0).x());
        assertEquals(8.5D, timeline.samples().get(1).x());
        assertEquals(1.0D, timeline.samples().get(2).x());
        assertEquals(9.5D, timeline.samples().get(3).x());
    }

    @Test
    void namedTimelinesApplyModesAndResetTheirOwnClock() {
        PlotPointTimeline timeline = new PlotPointTimeline();
        timeline.add("A", 8, 2, 0xFF123456, 100, PlotPointTimeline.XMode.TIME_OFFSET);
        timeline.add("B", 0, 5, 0xFF123456, 110, PlotPointTimeline.XMode.IGNORE);
        timeline.add("A", 12, 3, 0xFF123456, 120, PlotPointTimeline.XMode.TIME_OFFSET);
        timeline.add("A", 42, 4, 0xFF123456, 130, PlotPointTimeline.XMode.ABSOLUTE);

        assertEquals(8.0D, timeline.samples().get(0).x());
        assertEquals(0.0D, timeline.samples().get(1).x());
        assertEquals(9.0D, timeline.samples().get(2).x());
        assertEquals(42.0D, timeline.samples().get(3).x());

        PlotPointTimeline restored = new PlotPointTimeline();
        restored.fromTag(timeline.toTag());
        restored.reset("A");
        restored.add("A", 4, 6, 0xFF123456, 200, PlotPointTimeline.XMode.TIME_OFFSET);
        restored.add("B", 0, 7, 0xFF123456, 210, PlotPointTimeline.XMode.IGNORE);

        assertEquals(3, restored.samples().size());
        assertEquals(4.0D, restored.samples().get(1).x());
        assertEquals(5.0D, restored.samples().get(2).x());
    }

    @Test
    void clearStartsANewSessionWithoutOldSamplesOrTiming() {
        PlotPointTimeline timeline = new PlotPointTimeline();
        timeline.add("A", 5, 2, 0xFFFFFFFF, 100, PlotPointTimeline.XMode.TIME_OFFSET);
        var oldId = timeline.timelineId();

        timeline.clear();
        assertTrue(timeline.samples().isEmpty());
        assertTrue(timeline.seriesColors().isEmpty());
        assertEquals(0, timeline.revision());
        assertNotEquals(oldId, timeline.timelineId());

        timeline.add("A", 8, 3, 0xFFFFFFFF, 200, PlotPointTimeline.XMode.TIME_OFFSET);
        assertEquals(8.0D, timeline.samples().getFirst().x());
    }
}
