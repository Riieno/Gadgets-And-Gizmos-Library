package com.rieno.gadgetsandgizmos.lib.navigation;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

// Build and query a smooth cubic route which retains each authored waypoint as a segment boundary
public final class WaypointSpline {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final double EPSILON = 1.0E-9D;
    private static final int LENGTH_SAMPLES = 16;
    private static final int PROJECTION_REFINEMENT_STEPS = 10;
    private static final int DEFAULT_MAX_RAYCAST_SAMPLES = 16_384;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Defaults
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Retained authored waypoints
    private final List<Vec3> waypoints;
    // One cubic segment between each adjacent waypoint
    private final List<Segment> segments;
    // Approximate complete curve length
    private final double length;
    // Approximate distance at the start of each segment
    private final List<Double> segmentStarts;
    // Last immutable sample result retained for repeated render or guidance consumers
    private volatile SampleCache sampleCache;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize a spline from normalized waypoints
    private WaypointSpline(List<Vec3> waypoints) {
        this.waypoints = waypoints;
        List<Segment> built = new ArrayList<>();
        List<Double> starts = new ArrayList<>();
        double total = 0.0D;
        for (int idx = 0; idx + 1 < waypoints.size(); idx++) {
            starts.add(total);
            Vec3 startTangent = tangent(waypoints, idx);
            Vec3 endTangent = tangent(waypoints, idx + 1);
            Segment segment = Segment.create(idx, waypoints.get(idx), waypoints.get(idx + 1),
                    startTangent, endTangent);
            built.add(segment);
            total += segment.length();
        }
        segments = List.copyOf(built);
        segmentStarts = List.copyOf(starts);
        length = total;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Build a spline while discarding invalid and repeated adjacent points
    public static WaypointSpline of(List<Vec3> points) {
        if (points == null || points.isEmpty()) return new WaypointSpline(List.of());
        List<Vec3> normalized = new ArrayList<>();
        for (Vec3 point : points) {
            if (!isFinite(point)) continue;
            if (normalized.isEmpty()
                    || normalized.getLast().distanceToSqr(point) > EPSILON * EPSILON) {
                normalized.add(point);
            }
        }
        return new WaypointSpline(List.copyOf(normalized));
    }

    // Get detached authored waypoints
    public List<Vec3> waypoints() {
        return waypoints;
    }

    // Get the cubic legs between authored waypoints
    public List<Segment> segments() {
        return segments;
    }

    // Get the approximate complete curve length
    public double length() {
        return length;
    }

    // Check whether the spline contains a traversable segment
    public boolean isEmpty() {
        return segments.isEmpty();
    }

    // Return a new spline with one control inserted into an existing segment
    public WaypointSpline withWaypointAdded(int segmentIndex, Vec3 position) {
        if (segmentIndex < 0 || segmentIndex >= segments.size() || !isFinite(position)) return this;
        List<Vec3> updated = new ArrayList<>(waypoints);
        updated.add(segmentIndex + 1, position);
        return WaypointSpline.of(updated);
    }

    // Return a new spline with one authored control moved
    public WaypointSpline withWaypointMoved(int waypointIndex, Vec3 position) {
        if (waypointIndex < 0 || waypointIndex >= waypoints.size() || !isFinite(position)) return this;
        List<Vec3> updated = new ArrayList<>(waypoints);
        updated.set(waypointIndex, position);
        return WaypointSpline.of(updated);
    }

    // Return a new spline with one authored control removed while retaining a traversable route
    public WaypointSpline withWaypointRemoved(int waypointIndex) {
        if (waypointIndex < 0 || waypointIndex >= waypoints.size()
                || waypoints.size() <= 2) return this;
        List<Vec3> updated = new ArrayList<>(waypoints);
        updated.remove(waypointIndex);
        return WaypointSpline.of(updated);
    }

    // Sample the full curve into bounded straight chords
    public List<Vec3> sample(double maximumSpacing) {
        return sample(maximumSpacing, Integer.MAX_VALUE);
    }

    // Sample the full curve while retaining every authored segment boundary
    public List<Vec3> sample(double maximumSpacing, int maximumSamples) {
        if (waypoints.isEmpty()) return List.of();
        if (segments.isEmpty()) return waypoints;
        int sampleLimit = Math.max(segments.size() + 1, Math.max(2, maximumSamples));
        double requestedSpacing = positive(maximumSpacing, 1.0D);
        SampleCache cached = sampleCache;
        if (cached != null && Double.doubleToLongBits(cached.spacing())
                == Double.doubleToLongBits(requestedSpacing)
                && cached.maximumSamples() == sampleLimit) return cached.samples();
        double spacing = boundedSamplingSpacing(
                requestedSpacing, sampleLimit);
        List<Vec3> samples = new ArrayList<>();
        samples.add(waypoints.getFirst());
        for (Segment segment : segments) {
            int count = Math.max(1, (int) Math.ceil(segment.length() / spacing));
            for (int idx = 1; idx <= count; idx++) {
                samples.add(segment.pointAtFraction((double) idx / count));
            }
        }
        List<Vec3> result = List.copyOf(samples);
        sampleCache = new SampleCache(requestedSpacing, sampleLimit, result);
        return result;
    }

    // Project one position onto the complete curve
    public Projection project(Vec3 position) {
        return project(position, 0, 0.0D);
    }

    // Project one position onto an ordered suffix of the curve
    public Projection project(Vec3 position, int firstSegment) {
        return project(position, firstSegment, 0.0D);
    }

    // Project one position without moving behind committed progress on the first inspected segment
    public Projection project(Vec3 position, int firstSegment, double minimumFirstFraction) {
        if (segments.isEmpty() || !isFinite(position)) return Projection.notFound();
        int first = Math.max(0, Math.min(firstSegment, segments.size() - 1));
        double minimum = clamp(minimumFirstFraction, 0.0D, 1.0D);
        Projection best = Projection.notFound();
        double distanceBefore = segmentStarts.get(first);
        for (int idx = first; idx < segments.size(); idx++) {
            Segment segment = segments.get(idx);
            double segmentMinimum = idx == first ? minimum : 0.0D;
            double fraction = segment.nearestFraction(position, segmentMinimum);
            Vec3 projected = segment.pointAtFraction(fraction);
            double distanceSqr = projected.distanceToSqr(position);
            if (!best.found() || distanceSqr + EPSILON < best.distance() * best.distance()) {
                best = new Projection(true, idx, fraction, projected,
                        segment.tangentAtFraction(fraction), Math.sqrt(distanceSqr),
                        distanceBefore + segment.lengthToFraction(fraction));
            }
            distanceBefore += segment.length();
        }
        return best;
    }

    // Project one position onto exactly one ordered curve segment
    public Projection projectSegment(
            Vec3 position,
            int segmentIndex,
            double minimumFraction
    ) {
        if (segments.isEmpty() || !isFinite(position)
                || segmentIndex < 0 || segmentIndex >= segments.size()) {
            return Projection.notFound();
        }
        Segment segment = segments.get(segmentIndex);
        double fraction = segment.nearestFraction(
                position, clamp(minimumFraction, 0.0D, 1.0D));
        Vec3 projected = segment.pointAtFraction(fraction);
        return new Projection(true, segmentIndex, fraction, projected,
                segment.tangentAtFraction(fraction), projected.distanceTo(position),
                segmentStarts.get(segmentIndex)
                        + segment.lengthToFraction(fraction));
    }

    // Resolve a forward tracking point from the closest ordered curve position
    public TrackingTarget trackingTarget(
            Vec3 position,
            int firstSegment,
            double minimumFirstFraction,
            double lookahead
    ) {
        Projection projection = project(position, firstSegment, minimumFirstFraction);
        return trackingTarget(projection, lookahead);
    }

    // Resolve a forward tracking point from an already ordered projection
    public TrackingTarget trackingTarget(Projection projection, double lookahead) {
        if (projection == null || !projection.found()) return TrackingTarget.notFound();
        double targetDistance = clamp(projection.distanceAlongRoute()
                + Math.max(0.0D, finite(lookahead)), 0.0D, length);
        RoutePoint target = pointAtDistance(targetDistance);
        return new TrackingTarget(true, projection, target.segmentIndex(), target.fraction(),
                target.position(), target.tangent(), targetDistance);
    }

    // Get one position and tangent by approximate distance along the curve
    public RoutePoint pointAtDistance(double distance) {
        if (segments.isEmpty()) {
            Vec3 point = waypoints.isEmpty() ? Vec3.ZERO : waypoints.getFirst();
            return new RoutePoint(-1, 0.0D, point, Vec3.ZERO);
        }
        double requested = clamp(distance, 0.0D, length);
        int low = 0;
        int high = segments.size() - 1;
        while(low < high){
            int idx = (low + high) >>> 1;
            double end = segmentStarts.get(idx) + segments.get(idx).length();
            if(requested <= end) high = idx;
            else low = idx + 1;
        }
        Segment segment = segments.get(low);
        double remaining = requested - segmentStarts.get(low);
        double fraction = remaining <= 0.0D ? 0.0D : remaining >= segment.length() ? 1.0D
                : segment.fractionAtDistance(remaining);
        return new RoutePoint(low, fraction, segment.pointAtFraction(fraction),
                segment.tangentAtFraction(fraction));
    }

    // Pick the closest visible curve chord along one bounded ray
    public RayHit raycast(
            Vec3 rayOrigin,
            Vec3 rayDirection,
            double maximumDistance,
            double radius,
            double maximumSampleSpacing
    ) {
        return raycast(rayOrigin, rayDirection, maximumDistance, radius,
                maximumSampleSpacing, DEFAULT_MAX_RAYCAST_SAMPLES);
    }

    // Pick the closest visible curve chord with bounded sampling work
    public RayHit raycast(
            Vec3 rayOrigin,
            Vec3 rayDirection,
            double maximumDistance,
            double radius,
            double maximumSampleSpacing,
            int maximumSamples
    ) {
        if (segments.isEmpty() || !isFinite(rayOrigin) || !isFinite(rayDirection)) {
            return RayHit.notFound();
        }
        double range = positive(maximumDistance, 1.0D);
        double pickRadius = positive(radius, 0.25D);
        Vec3 direction = normalize(rayDirection, Vec3.ZERO);
        if (direction.lengthSqr() <= EPSILON) return RayHit.notFound();
        Vec3 rayEnd = rayOrigin.add(direction.scale(range));
        int sampleLimit = Math.max(segments.size() + 1, Math.max(2, maximumSamples));
        double spacing = boundedSamplingSpacing(
                positive(maximumSampleSpacing, 0.5D), sampleLimit);
        RayHit best = RayHit.notFound();
        double distanceBefore = 0.0D;
        for (Segment segment : segments) {
            int count = Math.max(1, (int) Math.ceil(segment.length() / spacing));
            Vec3 previous = segment.start();
            for (int idx = 1; idx <= count; idx++) {
                double endFraction = (double) idx / count;
                Vec3 current = segment.pointAtFraction(endFraction);
                ClosestSegments closest = closestSegments(rayOrigin, rayEnd, previous, current);
                double separationSqr = closest.first().distanceToSqr(closest.second());
                double rayDistance = rayOrigin.distanceTo(closest.first());
                if (separationSqr <= pickRadius * pickRadius
                        && (!best.found() || rayDistance + EPSILON < best.rayDistance()
                        || Math.abs(rayDistance - best.rayDistance()) <= EPSILON
                        && separationSqr < best.separation() * best.separation())) {
                    double startFraction = (double) (idx - 1) / count;
                    double fraction = startFraction
                            + (endFraction - startFraction) * closest.secondFraction();
                    Vec3 point = segment.pointAtFraction(fraction);
                    best = new RayHit(true, segment.index(), fraction, point, rayDistance,
                            Math.sqrt(separationSqr),
                            distanceBefore + segment.lengthToFraction(fraction));
                }
                previous = current;
            }
            distanceBefore += segment.length();
        }
        return best;
    }

    // Pick the closest authored waypoint along one bounded ray
    public WaypointHit raycastWaypoint(
            Vec3 rayOrigin,
            Vec3 rayDirection,
            double maximumDistance,
            double radius
    ) {
        if (waypoints.isEmpty() || !isFinite(rayOrigin) || !isFinite(rayDirection)) {
            return WaypointHit.notFound();
        }
        double range = positive(maximumDistance, 1.0D);
        double pickRadius = positive(radius, 0.35D);
        Vec3 direction = normalize(rayDirection, Vec3.ZERO);
        if (direction.lengthSqr() <= EPSILON) return WaypointHit.notFound();
        WaypointHit best = WaypointHit.notFound();
        for (int idx = 0; idx < waypoints.size(); idx++) {
            Vec3 offset = waypoints.get(idx).subtract(rayOrigin);
            double rayDistance = clamp(offset.dot(direction), 0.0D, range);
            Vec3 rayPoint = rayOrigin.add(direction.scale(rayDistance));
            double separation = rayPoint.distanceTo(waypoints.get(idx));
            if (separation <= pickRadius
                    && (!best.found() || rayDistance + EPSILON < best.rayDistance())) {
                best = new WaypointHit(true, idx, waypoints.get(idx), rayDistance, separation);
            }
        }
        return best;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            Helpers
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Resolve one shared waypoint tangent while bounding overshoot around short neighboring legs
    private static Vec3 tangent(List<Vec3> points, int idx) {
        if (points.size() < 2) return Vec3.ZERO;
        if (points.size() == 2) return points.get(1).subtract(points.get(0));
        if (idx <= 0) {
            Vec3 delta = points.get(1).subtract(points.get(0));
            return normalize(delta, Vec3.ZERO).scale(delta.length() * 0.75D);
        }
        if (idx >= points.size() - 1) {
            Vec3 delta = points.getLast().subtract(points.get(points.size() - 2));
            return normalize(delta, Vec3.ZERO).scale(delta.length() * 0.75D);
        }
        Vec3 incoming = points.get(idx).subtract(points.get(idx - 1));
        Vec3 outgoing = points.get(idx + 1).subtract(points.get(idx));
        double scale = Math.min(incoming.length(), outgoing.length()) * 0.75D;
        Vec3 direction = normalize(normalize(incoming, Vec3.ZERO)
                .add(normalize(outgoing, Vec3.ZERO)), normalize(outgoing, Vec3.ZERO));
        return direction.scale(scale);
    }

    // Find the closest pair between two finite line segments
    private static ClosestSegments closestSegments(Vec3 firstStart, Vec3 firstEnd,
                                                    Vec3 secondStart, Vec3 secondEnd) {
        Vec3 first = firstEnd.subtract(firstStart);
        Vec3 second = secondEnd.subtract(secondStart);
        Vec3 between = firstStart.subtract(secondStart);
        double firstLengthSqr = first.lengthSqr();
        double secondLengthSqr = second.lengthSqr();
        double firstSecond = first.dot(second);
        double firstBetween = first.dot(between);
        double secondBetween = second.dot(between);
        double denominator = firstLengthSqr * secondLengthSqr - firstSecond * firstSecond;
        double firstFraction = denominator <= EPSILON ? 0.0D
                : clamp((firstSecond * secondBetween
                - secondLengthSqr * firstBetween) / denominator, 0.0D, 1.0D);
        double secondFraction = secondLengthSqr <= EPSILON ? 0.0D
                : clamp((firstSecond * firstFraction + secondBetween) / secondLengthSqr,
                0.0D, 1.0D);
        firstFraction = firstLengthSqr <= EPSILON ? 0.0D
                : clamp((firstSecond * secondFraction - firstBetween) / firstLengthSqr,
                0.0D, 1.0D);
        secondFraction = secondLengthSqr <= EPSILON ? 0.0D
                : clamp((firstSecond * firstFraction + secondBetween) / secondLengthSqr,
                0.0D, 1.0D);
        return new ClosestSegments(firstStart.add(first.scale(firstFraction)),
                secondStart.add(second.scale(secondFraction)),
                firstFraction, secondFraction);
    }

    // Check whether one point contains finite coordinates
    private static boolean isFinite(Vec3 point) {
        return point != null && Double.isFinite(point.x)
                && Double.isFinite(point.y) && Double.isFinite(point.z);
    }

    // Count requested curve chords without overflowing a caller's useful limit
    private long chordCount(double spacing, int sampleLimit) {
        long count = 0L;
        for (Segment segment : segments) {
            count += Math.max(1L, (long) Math.ceil(segment.length() / spacing));
            if (count >= sampleLimit) return count;
        }
        return count;
    }

    // Increase sampling spacing only when needed to respect a complete-route sample limit
    private double boundedSamplingSpacing(double requestedSpacing, int sampleLimit) {
        int chordBudget = sampleLimit - 1;
        if (chordCount(requestedSpacing, sampleLimit) <= chordBudget) return requestedSpacing;
        double low = requestedSpacing;
        double high = segments.stream().mapToDouble(Segment::length)
                .max().orElse(requestedSpacing);
        for (int idx = 0; idx < 48; idx++) {
            double middle = (low + high) * 0.5D;
            if (chordCount(middle, sampleLimit) > chordBudget) low = middle;
            else high = middle;
        }
        return high;
    }

    // Normalize one vector
    private static Vec3 normalize(Vec3 value, Vec3 fallback) {
        return value != null && isFinite(value) && value.lengthSqr() > EPSILON
                ? value.normalize() : fallback;
    }

    // Normalize one finite number
    private static double finite(double val) {
        return Double.isFinite(val) ? val : 0.0D;
    }

    // Normalize one positive number
    private static double positive(double val, double fallback) {
        return Double.isFinite(val) && val > 0.0D ? val : fallback;
    }

    // Clamp one number
    private static double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, finite(val)));
    }

    // Store one cubic route leg
    public record Segment(int index, Vec3 start, Vec3 end,
                          Vec3 startTangent, Vec3 endTangent, double length) {
        // Create one segment and measure its curve length
        private static Segment create(int index, Vec3 start, Vec3 end,
                                      Vec3 startTangent, Vec3 endTangent) {
            Segment unmeasured = new Segment(index, start, end,
                    startTangent, endTangent, 0.0D);
            return new Segment(index, start, end, startTangent, endTangent,
                    unmeasured.lengthToFraction(1.0D));
        }

        // Sample one position along the cubic Hermite segment
        public Vec3 pointAtFraction(double fraction) {
            double t = clamp(fraction, 0.0D, 1.0D);
            double tSqr = t * t;
            double tCube = tSqr * t;
            double startWeight = 2.0D * tCube - 3.0D * tSqr + 1.0D;
            double startTangentWeight = tCube - 2.0D * tSqr + t;
            double endWeight = -2.0D * tCube + 3.0D * tSqr;
            double endTangentWeight = tCube - tSqr;
            return start.scale(startWeight)
                    .add(startTangent.scale(startTangentWeight))
                    .add(end.scale(endWeight))
                    .add(endTangent.scale(endTangentWeight));
        }

        // Sample one normalized travel tangent along the segment
        public Vec3 tangentAtFraction(double fraction) {
            double t = clamp(fraction, 0.0D, 1.0D);
            double tSqr = t * t;
            Vec3 derivative = start.scale(6.0D * tSqr - 6.0D * t)
                    .add(startTangent.scale(3.0D * tSqr - 4.0D * t + 1.0D))
                    .add(end.scale(-6.0D * tSqr + 6.0D * t))
                    .add(endTangent.scale(3.0D * tSqr - 2.0D * t));
            return normalize(derivative, normalize(end.subtract(start), Vec3.ZERO));
        }

        // Find the closest curve fraction at or after one minimum
        public double nearestFraction(Vec3 position, double minimumFraction) {
            if (!isFinite(position)) return clamp(minimumFraction, 0.0D, 1.0D);
            double minimum = clamp(minimumFraction, 0.0D, 1.0D);
            double bestFraction = minimum;
            double bestDistanceSqr = pointAtFraction(minimum).distanceToSqr(position);
            int samples = Math.max(2, LENGTH_SAMPLES);
            for (int idx = 1; idx <= samples; idx++) {
                double fraction = minimum + (1.0D - minimum) * idx / samples;
                double distanceSqr = pointAtFraction(fraction).distanceToSqr(position);
                if (distanceSqr < bestDistanceSqr) {
                    bestDistanceSqr = distanceSqr;
                    bestFraction = fraction;
                }
            }
            double radius = (1.0D - minimum) / samples;
            double low = Math.max(minimum, bestFraction - radius);
            double high = Math.min(1.0D, bestFraction + radius);
            for (int idx = 0; idx < PROJECTION_REFINEMENT_STEPS; idx++) {
                double left = low + (high - low) / 3.0D;
                double right = high - (high - low) / 3.0D;
                if (pointAtFraction(left).distanceToSqr(position)
                        <= pointAtFraction(right).distanceToSqr(position)) {
                    high = right;
                } else {
                    low = left;
                }
            }
            double refined = (low + high) * 0.5D;
            return pointAtFraction(refined).distanceToSqr(position) < bestDistanceSqr
                    ? refined : bestFraction;
        }

        // Measure the sampled arc length up to one fraction
        public double lengthToFraction(double fraction) {
            double target = clamp(fraction, 0.0D, 1.0D);
            if (target <= EPSILON) return 0.0D;
            Vec3 previous = start;
            double measured = 0.0D;
            for (int idx = 1; idx <= LENGTH_SAMPLES; idx++) {
                double currentFraction = target * idx / LENGTH_SAMPLES;
                Vec3 current = pointAtFraction(currentFraction);
                measured += previous.distanceTo(current);
                previous = current;
            }
            return measured;
        }

        // Resolve a curve fraction from sampled arc distance
        public double fractionAtDistance(double distance) {
            if (length <= EPSILON) return 1.0D;
            double target = clamp(distance, 0.0D, length);
            double low = 0.0D;
            double high = 1.0D;
            for (int idx = 0; idx < PROJECTION_REFINEMENT_STEPS + 2; idx++) {
                double middle = (low + high) * 0.5D;
                if (lengthToFraction(middle) < target) low = middle;
                else high = middle;
            }
            return (low + high) * 0.5D;
        }
    }

    // Store one closest curve projection
    public record Projection(boolean found, int segmentIndex, double fraction,
                             Vec3 position, Vec3 tangent, double distance,
                             double distanceAlongRoute) {
        // Create one missing projection
        public static Projection notFound() {
            return new Projection(false, -1, 0.0D, Vec3.ZERO,
                    Vec3.ZERO, Double.POSITIVE_INFINITY, 0.0D);
        }
    }

    // Store one forward tracking target
    public record TrackingTarget(boolean found, Projection projection,
                                 int segmentIndex, double fraction,
                                 Vec3 position, Vec3 tangent,
                                 double distanceAlongRoute) {
        // Create one missing tracking target
        public static TrackingTarget notFound() {
            return new TrackingTarget(false, Projection.notFound(), -1,
                    0.0D, Vec3.ZERO, Vec3.ZERO, 0.0D);
        }
    }

    // Store one curve point by approximate arc distance
    public record RoutePoint(int segmentIndex, double fraction,
                             Vec3 position, Vec3 tangent) {
    }

    // Store one curve ray hit
    public record RayHit(boolean found, int segmentIndex, double fraction,
                         Vec3 position, double rayDistance, double separation,
                         double distanceAlongRoute) {
        // Create one missing curve hit
        public static RayHit notFound() {
            return new RayHit(false, -1, 0.0D, Vec3.ZERO,
                    Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, 0.0D);
        }
    }

    // Store one authored-waypoint ray hit
    public record WaypointHit(boolean found, int waypointIndex, Vec3 position,
                              double rayDistance, double separation) {
        // Create one missing waypoint hit
        public static WaypointHit notFound() {
            return new WaypointHit(false, -1, Vec3.ZERO,
                    Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        }
    }

    // Store one closest point pair between two segments
    private record ClosestSegments(Vec3 first, Vec3 second,
                                   double firstFraction, double secondFraction) {
    }

    // Retain one repeated immutable sampling request
    private record SampleCache(double spacing, int maximumSamples, List<Vec3> samples) {
    }
}
