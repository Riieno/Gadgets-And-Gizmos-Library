package com.rieno.gadgetsandgizmos.lib.plot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/** Selects the visible detail of named plot samples in screen pixel space. */
public final class PlotPointViewport {
    public static final int MAX_VISIBLE_POINTS = 40;
    private static final double DETAIL_TOLERANCE_SQUARED = 2.5D * 2.5D;

    private PlotPointViewport() {
    }

    public record Point(double x, double y, boolean sample) {
        public Point(double x, double y) {
            this(x, y, true);
        }
    }

    public record Stroke(String name, int color, List<Point> points) {
        public Stroke {
            points = List.copyOf(points);
        }
    }

    public record Marker(String name, int color, Point point) {
    }

    public record Frame(List<Stroke> strokes, Map<String, Integer> seriesColors, List<Marker> markers) {
        public Frame(List<Stroke> strokes, Map<String, Integer> seriesColors) {
            this(strokes, seriesColors, List.of());
        }

        public Frame {
            strokes = List.copyOf(strokes);
            seriesColors = Collections.unmodifiableMap(new LinkedHashMap<>(seriesColors));
            markers = List.copyOf(markers);
        }
    }

    private record Segment(Point start, Point end) {
    }

    private record DetailRange(int stroke, int start, int end, int peak, double distance) {
    }

    private record DetailGap(int stroke, int start, int end) {
    }

    public static Frame build(List<PlotPointTimeline.Sample> samples,
                              double minX, double maxX, double minY, double maxY,
                              int width, int height, Set<String> hiddenNames) {
        if (samples == null || width < 1 || height < 1 || !Double.isFinite(minX)
                || !Double.isFinite(maxX) || !Double.isFinite(minY) || !Double.isFinite(maxY)
                || maxX <= minX || maxY <= minY) {
            return new Frame(List.of(), Map.of());
        }
        Map<String, List<PlotPointTimeline.Sample>> series = new LinkedHashMap<>();
        Map<String, Integer> colors = new LinkedHashMap<>();
        for (PlotPointTimeline.Sample sample : samples) {
            if (sample == null) continue;
            colors.putIfAbsent(sample.name(), sample.color());
            if (hiddenNames == null || !hiddenNames.contains(sample.name())) {
                series.computeIfAbsent(sample.name(), ignored -> new ArrayList<>()).add(sample);
            }
        }
        List<Stroke> strokes = new ArrayList<>();
        int visibleSamples = 0;
        List<Marker> exactMarkers = new ArrayList<>();
        double worldColumnWidth = (maxX - minX) / width;
        for (Map.Entry<String, List<PlotPointTimeline.Sample>> entry : series.entrySet()) {
            List<PlotPointTimeline.Sample> source = entry.getValue();
            for (PlotPointTimeline.Sample sample : source) {
                Point projected = project(sample, minX, maxX, minY, maxY, width, height);
                if (inside(projected, width, height)) {
                    visibleSamples++;
                    if (exactMarkers.size() < MAX_VISIBLE_POINTS) {
                        exactMarkers.add(new Marker(entry.getKey(), sample.color(), projected));
                    }
                }
            }
            List<PlotPointTimeline.Sample> points = collapseWorldColumns(source, worldColumnWidth);
            if (points.size() == 1) {
                Point dot = project(points.getFirst(), minX, maxX, minY, maxY, width, height);
                if (inside(dot, width, height)) {
                    strokes.add(new Stroke(entry.getKey(), points.getFirst().color(), List.of(dot)));
                }
                continue;
            }
            List<Point> path = new ArrayList<>();
            int pathColor = 0;
            Point previous = project(points.getFirst(), minX, maxX, minY, maxY, width, height);
            for (int index = 1; index < points.size(); index++) {
                PlotPointTimeline.Sample sample = points.get(index);
                Point current = project(sample, minX, maxX, minY, maxY, width, height);
                Segment visible = clip(previous, current, width, height);
                if (visible == null) {
                    flush(strokes, entry.getKey(), pathColor, path);
                } else {
                    if (!path.isEmpty() && (pathColor != sample.color()
                            || !same(path.getLast(), visible.start()))) {
                        flush(strokes, entry.getKey(), pathColor, path);
                    }
                    if (path.isEmpty()) {
                        pathColor = sample.color();
                        path.add(visible.start());
                    }
                    if (!same(path.getLast(), visible.end())) path.add(visible.end());
                    else if (visible.end().sample() && !path.getLast().sample()) {
                        path.set(path.size() - 1, visible.end());
                    }
                }
                previous = current;
            }
            flush(strokes, entry.getKey(), pathColor, path);
        }
        if (visibleSamples <= MAX_VISIBLE_POINTS) {
            return new Frame(strokes, colors, exactMarkers);
        }
        List<Stroke> markerPaths = new ArrayList<>();
        for (Stroke stroke : strokes) {
            List<Point> candidates = stroke.points().stream().filter(Point::sample).toList();
            if (!candidates.isEmpty()) markerPaths.add(new Stroke(stroke.name(), stroke.color(), candidates));
        }
        List<Marker> markers = new ArrayList<>();
        for (Stroke selected : limitDetail(markerPaths, visibleSamples)) {
            for (Point point : selected.points()) {
                if (markers.size() == MAX_VISIBLE_POINTS) break;
                markers.add(new Marker(selected.name(), selected.color(), point));
            }
        }
        return new Frame(strokes, colors, markers);
    }

    private static List<PlotPointTimeline.Sample> collapseWorldColumns(
            List<PlotPointTimeline.Sample> source, double columnWidth) {
        if (source.size() < 5 || !(columnWidth > 0.0D)) return source;
        List<PlotPointTimeline.Sample> reduced = new ArrayList<>();
        int start = 0;
        while (start < source.size()) {
            int end = start + 1;
            double column = Math.floor(source.get(start).x() / columnWidth);
            int color = source.get(start).color();
            while (end < source.size() && source.get(end).color() == color
                    && Math.floor(source.get(end).x() / columnWidth) == column) end++;
            int minimum = start;
            int maximum = start;
            for (int index = start + 1; index < end; index++) {
                if (source.get(index).y() < source.get(minimum).y()) minimum = index;
                if (source.get(index).y() > source.get(maximum).y()) maximum = index;
            }
            int[] selected = {start, minimum, maximum, end - 1};
            java.util.Arrays.sort(selected);
            int previous = -1;
            for (int index : selected) {
                if (index != previous) reduced.add(source.get(index));
                previous = index;
            }
            start = end;
        }
        return reduced;
    }

    private static Point project(PlotPointTimeline.Sample sample,
                                 double minX, double maxX, double minY, double maxY,
                                 int width, int height) {
        return new Point((sample.x() - minX) / (maxX - minX) * Math.max(0, width - 1),
                (maxY - sample.y()) / (maxY - minY) * Math.max(0, height - 1));
    }

    private static boolean inside(Point point, int width, int height) {
        return Double.isFinite(point.x()) && Double.isFinite(point.y())
                && point.x() >= 0.0D && point.x() <= width - 1.0D
                && point.y() >= 0.0D && point.y() <= height - 1.0D;
    }

    private static boolean same(Point left, Point right) {
        return Math.abs(left.x() - right.x()) < 1.0E-6D
                && Math.abs(left.y() - right.y()) < 1.0E-6D;
    }

    private static Segment clip(Point start, Point end, int width, int height) {
        if (!Double.isFinite(start.x()) || !Double.isFinite(start.y())
                || !Double.isFinite(end.x()) || !Double.isFinite(end.y())) return null;
        double dx = end.x() - start.x();
        double dy = end.y() - start.y();
        if (!Double.isFinite(dx) || !Double.isFinite(dy)) return null;
        double[] bounds = {0.0D, 1.0D};
        if (!clipEdge(-dx, start.x(), bounds) || !clipEdge(dx, width - 1.0D - start.x(), bounds)
                || !clipEdge(-dy, start.y(), bounds)
                || !clipEdge(dy, height - 1.0D - start.y(), bounds)) return null;
        return new Segment(new Point(start.x() + bounds[0] * dx, start.y() + bounds[0] * dy,
                bounds[0] == 0.0D && start.sample()),
                new Point(start.x() + bounds[1] * dx, start.y() + bounds[1] * dy,
                        bounds[1] == 1.0D && end.sample()));
    }

    private static boolean clipEdge(double direction, double distance, double[] bounds) {
        if (direction == 0.0D) return distance >= 0.0D;
        double factor = distance / direction;
        if (direction < 0.0D) bounds[0] = Math.max(bounds[0], factor);
        else bounds[1] = Math.min(bounds[1], factor);
        return bounds[0] <= bounds[1];
    }

    private static void flush(List<Stroke> strokes, String name, int color, List<Point> path) {
        if (path.isEmpty()) return;
        strokes.add(new Stroke(name, color, path));
        path.clear();
    }

    private static List<Stroke> limitDetail(List<Stroke> strokes, int visibleSamples) {
        int total = 0;
        for (Stroke stroke : strokes) total += stroke.points().size();
        if (total <= MAX_VISIBLE_POINTS) return strokes;

        List<Integer> priority = new ArrayList<>();
        double[] spans = new double[strokes.size()];
        for (int index = 0; index < strokes.size(); index++) {
            priority.add(index);
            spans[index] = strokeSpan(strokes.get(index));
        }
        priority.sort(Comparator.<Integer>comparingDouble(index -> spans[index])
                .reversed().thenComparingInt(index -> index));
        boolean[] selected = new boolean[strokes.size()];
        int selectedCount = 0;
        for (int index : priority) {
            int minimum = Math.min(2, strokes.get(index).points().size());
            if (selectedCount + minimum > MAX_VISIBLE_POINTS) continue;
            selected[index] = true;
            selectedCount += minimum;
        }

        boolean[][] retained = new boolean[strokes.size()][];
        PriorityQueue<DetailRange> ranges = new PriorityQueue<>(
                Comparator.comparingDouble(DetailRange::distance).reversed()
                        .thenComparingInt(DetailRange::stroke).thenComparingInt(DetailRange::start));
        for (int index = 0; index < strokes.size(); index++) {
            if (!selected[index]) continue;
            List<Point> path = strokes.get(index).points();
            retained[index] = new boolean[path.size()];
            retained[index][0] = true;
            retained[index][path.size() - 1] = true;
            addRange(ranges, index, 0, path.size() - 1, path);
        }

        while (selectedCount < MAX_VISIBLE_POINTS && !ranges.isEmpty()
                && ranges.peek().distance() > DETAIL_TOLERANCE_SQUARED) {
            DetailRange range = ranges.remove();
            retained[range.stroke()][range.peak()] = true;
            selectedCount++;
            List<Point> path = strokes.get(range.stroke()).points();
            addRange(ranges, range.stroke(), range.start(), range.peak(), path);
            addRange(ranges, range.stroke(), range.peak(), range.end(), path);
        }

        int flatTarget = Math.min(MAX_VISIBLE_POINTS,
                Math.max(selectedCount, (int) Math.ceil((double) MAX_VISIBLE_POINTS
                        * MAX_VISIBLE_POINTS / Math.max(1, visibleSamples))));
        PriorityQueue<DetailGap> gaps = new PriorityQueue<>(
                Comparator.<DetailGap>comparingInt(gap -> gap.end() - gap.start()).reversed()
                        .thenComparingInt(DetailGap::stroke).thenComparingInt(DetailGap::start));
        for (int index = 0; index < strokes.size(); index++) {
            if (!selected[index]) continue;
            int previous = -1;
            for (int point = 0; point < retained[index].length; point++) {
                if (!retained[index][point]) continue;
                if (previous >= 0 && point - previous > 1) {
                    gaps.add(new DetailGap(index, previous, point));
                }
                previous = point;
            }
        }
        while (selectedCount < flatTarget && !gaps.isEmpty()) {
            DetailGap gap = gaps.remove();
            int middle = (gap.start() + gap.end()) >>> 1;
            retained[gap.stroke()][middle] = true;
            selectedCount++;
            if (middle - gap.start() > 1) gaps.add(new DetailGap(gap.stroke(), gap.start(), middle));
            if (gap.end() - middle > 1) gaps.add(new DetailGap(gap.stroke(), middle, gap.end()));
        }

        List<Stroke> reduced = new ArrayList<>();
        for (int index = 0; index < strokes.size(); index++) {
            if (!selected[index]) continue;
            List<Point> points = new ArrayList<>();
            for (int point = 0; point < retained[index].length; point++) {
                if (retained[index][point]) points.add(strokes.get(index).points().get(point));
            }
            reduced.add(new Stroke(strokes.get(index).name(), strokes.get(index).color(), points));
        }
        return reduced;
    }

    private static double strokeSpan(Stroke stroke) {
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (Point point : stroke.points()) {
            minX = Math.min(minX, point.x());
            maxX = Math.max(maxX, point.x());
            minY = Math.min(minY, point.y());
            maxY = Math.max(maxY, point.y());
        }
        return Math.max(maxX - minX, maxY - minY);
    }

    private static void addRange(PriorityQueue<DetailRange> ranges, int stroke,
                                 int start, int end, List<Point> path) {
        if (end - start < 2) return;
        int peak = -1;
        double distance = 0.0D;
        for (int index = start + 1; index < end; index++) {
            double candidate = distanceSquared(path.get(index), path.get(start), path.get(end));
            if (candidate > distance) {
                distance = candidate;
                peak = index;
            }
        }
        if (peak >= 0) ranges.add(new DetailRange(stroke, start, end, peak, distance));
    }

    private static double distanceSquared(Point point, Point start, Point end) {
        double dx = end.x() - start.x();
        double dy = end.y() - start.y();
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared == 0.0D) {
            double px = point.x() - start.x();
            double py = point.y() - start.y();
            return px * px + py * py;
        }
        double fraction = Math.max(0.0D, Math.min(1.0D,
                ((point.x() - start.x()) * dx + (point.y() - start.y()) * dy) / lengthSquared));
        double px = point.x() - (start.x() + fraction * dx);
        double py = point.y() - (start.y() + fraction * dy);
        return px * px + py * py;
    }
}
