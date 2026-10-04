package com.rieno.gadgetsandgizmos.lib.plot;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PlotPointPerformanceTest {
    @Test
    void zoomedOutStraightLineUsesOnlyItsEndsAndZoomRevealsSmallChanges() {
        List<PlotPointTimeline.Sample> samples = new ArrayList<>();
        for (int index = 0; index < 1_000; index++) {
            double bump = index == 500 ? 0.2D : 0.0D;
            samples.add(new PlotPointTimeline.Sample("Trace", index, bump, 0xFF25C6D8));
        }

        PlotPointViewport.Frame distant = PlotPointViewport.build(
                samples, 0, 1_000, -100, 100, 100, 100, Set.of());
        assertEquals(1, distant.strokes().size());
        assertEquals(2, distant.markers().size());

        PlotPointViewport.Frame closer = PlotPointViewport.build(
                samples, 480, 520, -1, 1, 100, 100, Set.of());
        assertTrue(closer.markers().size() > 2);
    }

    @Test
    void zoomingInRevealsSeparateSamplesEvenOnAStraightLine() {
        List<PlotPointTimeline.Sample> samples = new ArrayList<>();
        for (int index = 0; index < 1_000; index++) {
            samples.add(new PlotPointTimeline.Sample("Trace", index, 0, 0xFFFFFFFF));
        }
        PlotPointViewport.Frame distant = PlotPointViewport.build(
                samples, 0, 1_000, -1, 1, 100, 100, Set.of());
        PlotPointViewport.Frame close = PlotPointViewport.build(
                samples, 400, 420, -1, 1, 100, 100, Set.of());
        assertEquals(2, distant.markers().size());
        assertTrue(close.markers().size() >= 20);
    }

    @Test
    void absoluteXRetainsVerticallySeparatedSamples() {
        List<PlotPointTimeline.Sample> samples = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            samples.add(new PlotPointTimeline.Sample("Trace", 5, index, 0xFFFFFFFF));
        }
        PlotPointViewport.Frame frame = PlotPointViewport.build(
                samples, 0, 10, -1, 40, 100, 100, Set.of());
        assertEquals(40, frame.markers().size());
        assertTrue(frame.strokes().getFirst().points().stream().anyMatch(point -> point.y() < 5));
        assertTrue(frame.strokes().getFirst().points().stream().anyMatch(point -> point.y() > 90));
    }

    @Test
    void denseSpikesKeepTheirPeaksWithinTheGlobalPointBudget() {
        List<PlotPointTimeline.Sample> samples = new ArrayList<>();
        for (int index = 0; index < 5_000; index++) {
            double y = index == 1_500 || index == 3_500 ? 100.0D : index % 11 * 0.1D;
            samples.add(new PlotPointTimeline.Sample("Spikes", index, y, 0xFFFF00FF));
            samples.add(new PlotPointTimeline.Sample("Other", index, index % 17 * 0.1D, 0xFF00FFFF));
        }
        PlotPointViewport.Frame frame = PlotPointViewport.build(
                samples, 0, 5_000, -5, 105, 500, 300, Set.of());
        assertTrue(frame.markers().size() <= PlotPointViewport.MAX_VISIBLE_POINTS);
        assertTrue(frame.strokes().stream().flatMap(stroke -> stroke.points().stream())
                .anyMatch(point -> Math.abs(point.x() - 150) < 3 && point.y() < 30));
        assertTrue(frame.strokes().stream().flatMap(stroke -> stroke.points().stream())
                .anyMatch(point -> Math.abs(point.x() - 350) < 3 && point.y() < 30));
        assertTrue(frame.markers().stream()
                .anyMatch(marker -> Math.abs(marker.point().x() - 150) < 3 && marker.point().y() < 30));
        assertTrue(frame.markers().stream()
                .anyMatch(marker -> Math.abs(marker.point().x() - 350) < 3 && marker.point().y() < 30));
    }

    @Test
    void visibleDetailIncreasesGraduallyAsTheViewportNarrows() {
        List<PlotPointTimeline.Sample> samples = new ArrayList<>();
        for (int index = 0; index < 1_000; index++) {
            samples.add(new PlotPointTimeline.Sample("Trace", index, 0, 0xFFFFFFFF));
        }
        int distant = PlotPointViewport.build(samples, 0, 1_000, -1, 1,
                100, 100, Set.of()).markers().size();
        int middle = PlotPointViewport.build(samples, 400, 500, -1, 1,
                100, 100, Set.of()).markers().size();
        int close = PlotPointViewport.build(samples, 440, 460, -1, 1,
                100, 100, Set.of()).markers().size();
        assertTrue(distant < middle && middle < close);
        assertTrue(close <= PlotPointViewport.MAX_VISIBLE_POINTS);
    }

    @Test
    void cullsOffscreenSegmentsAndClipsCrossingsBeforeDrawing() {
        List<PlotPointTimeline.Sample> samples = List.of(
                new PlotPointTimeline.Sample("Outside", -1_000_000, 1_000_000, 0xFFFFFFFF),
                new PlotPointTimeline.Sample("Outside", -900_000, 900_000, 0xFFFFFFFF),
                new PlotPointTimeline.Sample("Crossing", -1_000_000, 0, 0xFFFFFFFF),
                new PlotPointTimeline.Sample("Crossing", 1_000_000, 0, 0xFFFFFFFF));
        PlotPointViewport.Frame frame = PlotPointViewport.build(
                samples, -10, 10, -10, 10, 120, 80, Set.of());
        assertEquals(1, frame.strokes().size());
        for (PlotPointViewport.Point point : frame.strokes().getFirst().points()) {
            assertTrue(point.x() >= 0 && point.x() <= 119);
            assertTrue(point.y() >= 0 && point.y() <= 79);
        }
        assertTrue(PlotPointViewport.build(samples, -10, 10, -10, 10,
                120, 80, Set.of("Crossing")).strokes().isEmpty());
    }

    @Test
    void keepsLineFromOffscreenSampleButDoesNotMarkClippedEndpoint() {
        List<PlotPointTimeline.Sample> samples = List.of(
                new PlotPointTimeline.Sample("Trace", -10, 5, 0xFFFFFFFF),
                new PlotPointTimeline.Sample("Trace", 5, 5, 0xFFFFFFFF),
                new PlotPointTimeline.Sample("Trace", 20, 5, 0xFFFFFFFF));
        PlotPointViewport.Frame frame = PlotPointViewport.build(
                samples, 0, 10, 0, 10, 101, 101, Set.of());
        List<PlotPointViewport.Point> path = frame.strokes().getFirst().points();
        assertEquals(3, path.size());
        assertEquals(0.0D, path.getFirst().x());
        assertFalse(path.getFirst().sample());
        assertTrue(path.get(1).sample());
        assertEquals(100.0D, path.getLast().x());
        assertFalse(path.getLast().sample());
    }

    @Test
    void preservesEveryCrossingEvenWhenThereAreMoreThanFortySegments() {
        List<PlotPointTimeline.Sample> samples = new ArrayList<>();
        for (int index = 0; index < 50; index++) {
            samples.add(new PlotPointTimeline.Sample("Trace", index * 2, 5, 0xFFFFFFFF));
            samples.add(new PlotPointTimeline.Sample("Trace", index * 2 + 1, 50, 0xFFFFFFFF));
        }
        PlotPointViewport.Frame frame = PlotPointViewport.build(
                samples, 0, 100, 0, 10, 501, 101, Set.of());
        assertTrue(frame.strokes().size() >= 50);
        assertTrue(frame.strokes().stream().allMatch(stroke -> stroke.points().size() >= 2));
    }

    @Test
    void preservesColorChangesInsideAVisibleSeries() {
        List<PlotPointTimeline.Sample> samples = List.of(
                new PlotPointTimeline.Sample("Trace", 0, 0, 0xFFFF0000),
                new PlotPointTimeline.Sample("Trace", 1, 0, 0xFFFF0000),
                new PlotPointTimeline.Sample("Trace", 2, 0, 0xFF0000FF));
        PlotPointViewport.Frame frame = PlotPointViewport.build(
                samples, 0, 2, -1, 1, 100, 100, Set.of());
        assertEquals(2, frame.strokes().size());
        assertEquals(0xFFFF0000, frame.strokes().get(0).color());
        assertEquals(0xFF0000FF, frame.strokes().get(1).color());
    }

    @Test
    void horizontalPanKeepsTheSameInteriorLineSamples() {
        List<PlotPointTimeline.Sample> samples = new ArrayList<>();
        for (int index = 0; index <= 800; index++) {
            double x = index * 0.25D;
            double y = index % 47 == 0 ? 9.0D : index % 9 * 0.15D;
            samples.add(new PlotPointTimeline.Sample("Trace", x, y, 0xFF00FFFF));
        }
        PlotPointViewport.Frame before = PlotPointViewport.build(
                samples, 20, 120, -1, 11, 101, 101, Set.of());
        PlotPointViewport.Frame after = PlotPointViewport.build(
                samples, 30, 130, -1, 11, 101, 101, Set.of());
        assertEquals(interiorSamples(before, 20, 120, -1, 11, 101, 101, 40, 100, 0, 10),
                interiorSamples(after, 30, 130, -1, 11, 101, 101, 40, 100, 0, 10));
        assertEquals(interiorSegments(before, 20, 120, -1, 11, 101, 101, 40, 100, 0, 10),
                interiorSegments(after, 30, 130, -1, 11, 101, 101, 40, 100, 0, 10));
    }

    @Test
    void verticalPanKeepsTheSameInteriorLineSamples() {
        List<PlotPointTimeline.Sample> samples = new ArrayList<>();
        for (int index = 0; index <= 800; index++) {
            double x = index * 0.25D;
            double y = index % 47 == 0 ? 9.0D : index % 9 * 0.15D;
            samples.add(new PlotPointTimeline.Sample("Trace", x, y, 0xFF00FFFF));
        }
        PlotPointViewport.Frame before = PlotPointViewport.build(
                samples, 20, 120, -1, 11, 101, 101, Set.of());
        PlotPointViewport.Frame after = PlotPointViewport.build(
                samples, 20, 120, -3, 9, 101, 101, Set.of());
        assertEquals(interiorSamples(before, 20, 120, -1, 11, 101, 101, 40, 100, 0, 8),
                interiorSamples(after, 20, 120, -3, 9, 101, 101, 40, 100, 0, 8));
        assertEquals(interiorSegments(before, 20, 120, -1, 11, 101, 101, 40, 100, 0, 8),
                interiorSegments(after, 20, 120, -3, 9, 101, 101, 40, 100, 0, 8));
    }

    @Test
    void zoomRetainsSpikeCoordinatesWhileRevealingMoreSamples() {
        List<PlotPointTimeline.Sample> samples = new ArrayList<>();
        for (int index = 0; index <= 1_000; index++) {
            double x = index * 0.1D;
            double y = index == 500 || index == 750 ? 100.0D : 0.0D;
            samples.add(new PlotPointTimeline.Sample("Trace", x, y, 0xFF00FFFF));
        }
        PlotPointViewport.Frame wide = PlotPointViewport.build(
                samples, 0, 100, -1, 101, 101, 101, Set.of());
        PlotPointViewport.Frame close = PlotPointViewport.build(
                samples, 40, 80, -1, 101, 101, 101, Set.of());
        assertTrue(interiorSamples(wide, 0, 100, -1, 101, 101, 101,
                40, 80, 99, 101).contains("50000000:100000000"));
        assertTrue(interiorSamples(close, 40, 80, -1, 101, 101, 101,
                40, 80, 99, 101).contains("50000000:100000000"));
        assertTrue(interiorSamples(close, 40, 80, -1, 101, 101, 101,
                45, 75, -1, 101).size()
                > interiorSamples(wide, 0, 100, -1, 101, 101, 101,
                45, 75, -1, 101).size());
    }

    private static List<String> interiorSamples(PlotPointViewport.Frame frame,
                                                double minX, double maxX, double minY, double maxY,
                                                int width, int height, double lowX, double highX,
                                                double lowY, double highY) {
        return frame.strokes().stream()
                .flatMap(stroke -> stroke.points().stream())
                .filter(PlotPointViewport.Point::sample)
                .map(point -> worldPoint(point, minX, maxX, minY, maxY, width, height))
                .filter(point -> point[0] > lowX && point[0] < highX
                        && point[1] > lowY && point[1] < highY)
                .map(PlotPointPerformanceTest::coordinateKey)
                .distinct()
                .toList();
    }

    private static List<String> interiorSegments(PlotPointViewport.Frame frame,
                                                 double minX, double maxX, double minY, double maxY,
                                                 int width, int height, double lowX, double highX,
                                                 double lowY, double highY) {
        List<String> segments = new ArrayList<>();
        for (PlotPointViewport.Stroke stroke : frame.strokes()) {
            for (int index = 1; index < stroke.points().size(); index++) {
                double[] first = worldPoint(stroke.points().get(index - 1),
                        minX, maxX, minY, maxY, width, height);
                double[] second = worldPoint(stroke.points().get(index),
                        minX, maxX, minY, maxY, width, height);
                if (first[0] > lowX && first[0] < highX
                        && second[0] > lowX && second[0] < highX
                        && first[1] > lowY && first[1] < highY
                        && second[1] > lowY && second[1] < highY) {
                    segments.add(coordinateKey(first) + "->" + coordinateKey(second));
                }
            }
        }
        return segments;
    }

    private static double[] worldPoint(PlotPointViewport.Point point,
                                       double minX, double maxX, double minY, double maxY,
                                       int width, int height) {
        return new double[]{minX + point.x() * (maxX - minX) / (width - 1),
                maxY - point.y() * (maxY - minY) / (height - 1)};
    }

    private static String coordinateKey(double[] point) {
        return Math.round(point[0] * 1_000_000D) + ":"
                + Math.round(point[1] * 1_000_000D);
    }

    @Test
    void sendsOnlyNewSamplesAndFallsBackToFullSnapshotAfterReset() {
        PlotPointTimeline server = new PlotPointTimeline();
        for (int index = 0; index < 500; index++) {
            server.add("Trace", index, 0xFFFFFFFF, index);
        }
        PlotPointTimeline client = new PlotPointTimeline();
        assertTrue(client.applyUpdateTag(server.toUpdateTag(client.timelineId(), client.revision())));
        for (int index = 500; index < 520; index++) {
            server.add("Trace", index, 0xFFFFFFFF, index);
        }
        CompoundTag update = server.toUpdateTag(client.timelineId(), client.revision());
        assertTrue(update.getBoolean("Delta"));
        assertEquals(20, update.getList("Samples", Tag.TAG_COMPOUND).size());
        assertTrue(client.applyUpdateTag(update));
        assertEquals(server.samples(), client.samples());
        assertTrue(client.applyUpdateTag(update));
        assertEquals(server.samples(), client.samples());
        assertEquals(0, server.toUpdateTag(client.timelineId(), client.revision())
                .getList("Samples", Tag.TAG_COMPOUND).size());

        server.reset("Trace");
        assertFalse(server.toUpdateTag(client.timelineId(), client.revision()).getBoolean("Delta"));
        assertTrue(client.applyUpdateTag(server.toUpdateTag(client.timelineId(), client.revision())));
        assertTrue(client.samples().isEmpty());
    }

    @Test
    void keepsAllSamplesAndPagesTheHistory() {
        PlotPointTimeline timeline = new PlotPointTimeline();
        for (int index = 0; index < 10_000; index++) {
            timeline.add("Trace", index, 0xFFFFFFFF, index);
        }
        assertEquals(10_000, timeline.samples().size());
        assertEquals(0.0D, timeline.samples().getFirst().y());
        PlotPointTimeline client = new PlotPointTimeline();
        int pages = 0;
        do {
            CompoundTag update = timeline.toUpdateTag(client.timelineId(), client.revision());
            assertTrue(update.getList("Samples", Tag.TAG_COMPOUND).size()
                    <= PlotPointTimeline.MAX_UPDATE_SAMPLES);
            assertTrue(client.applyUpdateTag(update));
            pages++;
            if (!update.getBoolean("HasMore")) break;
        } while (pages < 100);
        assertTrue(pages > 1);
        assertEquals(timeline.samples(), client.samples());
        PlotPointTimeline restored = new PlotPointTimeline();
        restored.fromTag(timeline.toTag());
        assertEquals(timeline.samples(), restored.samples());
    }

    @Test
    void resetKeepsOtherSeriesAvailableThroughPagedUpdates() {
        PlotPointTimeline server = new PlotPointTimeline();
        for (int index = 0; index < 1_000; index++) {
            server.add(index % 2 == 0 ? "Keep" : "Remove", index, 0xFFFFFFFF, index);
        }
        PlotPointTimeline client = new PlotPointTimeline();
        assertTrue(client.applyUpdateTag(server.toUpdateTag(client.timelineId(), client.revision())));
        server.reset("Remove");
        CompoundTag update = server.toUpdateTag(client.timelineId(), client.revision());
        assertFalse(update.getBoolean("Delta"));
        assertTrue(client.applyUpdateTag(update));
        assertEquals(500, client.samples().size());
        server.add("Keep", 1_000, 0xFFFFFFFF, 1_000);
        assertTrue(client.applyUpdateTag(server.toUpdateTag(client.timelineId(), client.revision())));
        assertEquals(server.samples(), client.samples());
    }
}
