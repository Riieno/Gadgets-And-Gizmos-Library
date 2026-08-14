package com.rieno.gadgetsandgizmos.lib.graph.render;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.List;

// Build orthogonal wires and hit areas without depending on a specific renderer
public final class GraphWireGeometry {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the graph wire geometry
    private GraphWireGeometry() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the segments
    public static List<Segment> segments(double x1, double y1, double x2, double y2) {
        double bendX = (x1 + x2) * 0.5D;
        return List.of(
                new Segment(new Point(x1, y1), new Point(bendX, y1)),
                new Segment(new Point(bendX, y1), new Point(bendX, y2)),
                new Segment(new Point(bendX, y2), new Point(x2, y2)));
    }

    // Get the point
    public static Point pointAt(double x1, double y1, double x2, double y2, double amount) {
        List<Segment> parts = segments(x1, y1, x2, y2);
        double totalLength = parts.stream().mapToDouble(Segment::length).sum();
        if (totalLength <= 0.0D) return new Point(x1, y1);
        double remaining = Math.max(0.0D, Math.min(1.0D, amount)) * totalLength;
        for (Segment segment : parts) {
            double length = segment.length();
            if (remaining <= length || segment == parts.getLast()) {
                double local = length <= 0.0D ? 0.0D : Math.min(1.0D, remaining / length);
                return new Point(
                        segment.start().x() + (segment.end().x() - segment.start().x()) * local,
                        segment.start().y() + (segment.end().y() - segment.start().y()) * local);
            }
            remaining -= length;
        }
        return new Point(x2, y2);
    }

    // Get the distance squared to wire
    public static double distanceSquaredToWire(double x, double y,
                                               double x1, double y1, double x2, double y2) {
        double closest = Double.MAX_VALUE;
        for (Segment segment : segments(x1, y1, x2, y2)) {
            closest = Math.min(closest, distanceSquared(x, y, segment.start(), segment.end()));
        }
        return closest;
    }

    // Get the distance squared
    private static double distanceSquared(double x, double y, Point start, Point end) {
        double dx = end.x() - start.x();
        double dy = end.y() - start.y();
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared <= 0.0D) return square(x - start.x()) + square(y - start.y());
        double amount = Math.max(0.0D, Math.min(1.0D,
                ((x - start.x()) * dx + (y - start.y()) * dy) / lengthSquared));
        return square(x - (start.x() + dx * amount)) + square(y - (start.y() + dy * amount));
    }

    // Get the square
    private static double square(double val) {
        return val * val;
    }

    // Store the point
    public record Point(double x, double y) {
    }

    // Store the segment
    public record Segment(Point start, Point end) {
        // Get the length
        public double length() {
            return Math.abs(end.x() - start.x()) + Math.abs(end.y() - start.y());
        }
    }
}
