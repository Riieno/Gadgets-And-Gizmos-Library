package com.rieno.gadgetsandgizmos.lib.navigation;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Resolves deterministic right-of-way between vehicles on retained routes. */
public final class RouteTrafficPriority {
    private static final double EPSILON = 1.0E-6D;

    private RouteTrafficPriority() {
    }

    /** Resolve the most urgent route conflict for one vehicle. */
    public static Decision resolve(
            Participant subject,
            Collection<Participant> traffic,
            Settings settings
    ) {
        if (subject == null || subject.route().size() < 2) return Decision.clear();
        Settings safeSettings = settings == null ? Settings.DEFAULT : settings;
        Path subjectPath = Path.from(subject, safeSettings.lookaheadDistance());
        Decision selectedYield = null;
        Decision selectedConflict = null;
        Collection<Participant> candidates = traffic == null ? List.of() : traffic;
        for (Participant other : candidates) {
            if (other == null || subject.vehicleId().equals(other.vehicleId())
                    || other.route().size() < 2) {
                continue;
            }
            Decision decision = resolvePair(
                    subject, subjectPath, other, safeSettings);
            if (!decision.conflict()) continue;
            if (selectedConflict == null
                    || decision.distanceToConflict()
                    < selectedConflict.distanceToConflict()) {
                selectedConflict = decision;
            }
            if (decision.yield() && (selectedYield == null
                    || decision.yieldDistance() < selectedYield.yieldDistance())) {
                selectedYield = decision;
            }
        }
        return selectedYield != null ? selectedYield
                : selectedConflict != null ? selectedConflict : Decision.clear();
    }

    /**
     * Check whether a yielding vehicle should clear an imminent crossing or
     * head-on encounter by retreating. The host remains responsible for
     * validating and executing a reverse manoeuvre.
     */
    public static boolean requiresRetreat(
            Decision decision,
            double liveTravelClearance,
            double requiredClearance
    ) {
        if (decision == null || !decision.yield()
                || decision.encounter() == Encounter.NONE
                || decision.encounter() == Encounter.SAME_DIRECTION) {
            return false;
        }
        double available = Double.isFinite(liveTravelClearance)
                ? Math.max(0.0D, liveTravelClearance) : Double.POSITIVE_INFINITY;
        double required = nonNegative(requiredClearance);
        return available + EPSILON < required;
    }

    // Resolve one pair without transferring route ownership.
    private static Decision resolvePair(
            Participant subject,
            Path subjectPath,
            Participant other,
            Settings settings
    ) {
        double centerDistance = measuredDistance(
                subject.position(), other.position(),
                subject.mode() == SablePathfinder.RouteMode.GROUND
                        && other.mode() == SablePathfinder.RouteMode.GROUND);
        if (centerDistance > settings.coordinationDistance()) return Decision.clear();
        Path otherPath = Path.from(other, settings.lookaheadDistance());
        Conflict selected = Conflict.none();
        double requiredSeparation = subject.clearanceRadius()
                + other.clearanceRadius() + settings.clearanceBuffer();
        boolean ground = subject.mode() == SablePathfinder.RouteMode.GROUND
                && other.mode() == SablePathfinder.RouteMode.GROUND;
        for (PathSegment first : subjectPath.segments()) {
            for (PathSegment second : otherPath.segments()) {
                SegmentApproach approach = closestApproach(first, second, ground);
                if (approach.separation() > requiredSeparation + EPSILON) continue;
                if (ground && approach.verticalSeparation()
                        > subject.verticalClearance() + other.verticalClearance()
                        + settings.clearanceBuffer()) {
                    continue;
                }
                double subjectDistance = first.distanceAtStart()
                        + first.length() * approach.firstFraction();
                double otherDistance = second.distanceAtStart()
                        + second.length() * approach.secondFraction();
                if (!selected.found()
                        || subjectDistance < selected.subjectDistance() - EPSILON
                        || Math.abs(subjectDistance - selected.subjectDistance()) <= EPSILON
                        && otherDistance < selected.otherDistance()) {
                    double alignment = first.direction().dot(second.direction());
                    Encounter encounter = alignment >= 0.5D
                            ? Encounter.SAME_DIRECTION
                            : alignment <= -0.5D ? Encounter.HEAD_ON
                            : Encounter.CROSSING;
                    selected = new Conflict(
                            approach.midpoint(), subjectDistance, otherDistance,
                            approach.separation(), encounter, true);
                }
            }
        }
        if (!selected.found()) return Decision.clear();

        double subjectSpeed = subject.travelSpeed(settings.minimumTravelSpeed());
        double otherSpeed = other.travelSpeed(settings.minimumTravelSpeed());
        double subjectDistance = selected.subjectDistance();
        double otherDistance = selected.otherDistance();
        boolean mustYield;
        if (selected.encounter() == Encounter.SAME_DIRECTION) {
            boolean subjectBehind = subjectDistance > otherDistance + settings.distanceTieTolerance();
            boolean otherBehind = otherDistance > subjectDistance + settings.distanceTieTolerance();
            if (subjectBehind) {
                double closingSpeed = Math.max(
                        subject.measuredSpeed() - other.measuredSpeed(),
                        subject.expectedSpeed() - other.expectedSpeed());
                boolean alreadyClose = centerDistance <= requiredSeparation;
                if (!alreadyClose && closingSpeed <= EPSILON) return Decision.clear();
                double catchSeconds = alreadyClose ? 0.0D
                        : Math.max(0.0D, centerDistance - requiredSeparation) / closingSpeed;
                if (catchSeconds > settings.minimumTimeSeparation()) return Decision.clear();
                mustYield = true;
            } else if (otherBehind) {
                mustYield = false;
            } else {
                mustYield = compareVehicleIds(subject.vehicleId(), other.vehicleId()) > 0;
            }
        } else {
            if (selected.encounter() == Encounter.HEAD_ON
                    && directlyApproaching(subject, other)) {
                double combinedSpeed = Math.max(EPSILON, subjectSpeed + otherSpeed);
                subjectDistance = centerDistance * subjectSpeed / combinedSpeed;
                otherDistance = centerDistance - subjectDistance;
            }
            double subjectArrival = subjectDistance / subjectSpeed;
            double otherArrival = otherDistance / otherSpeed;
            if (Math.abs(subjectArrival - otherArrival)
                    >= settings.minimumTimeSeparation()) {
                return Decision.clear();
            }
            if (subjectDistance + settings.distanceTieTolerance() < otherDistance) {
                mustYield = false;
            } else if (otherDistance + settings.distanceTieTolerance() < subjectDistance) {
                mustYield = true;
            } else {
                mustYield = compareVehicleIds(subject.vehicleId(), other.vehicleId()) > 0;
            }
        }
        double yieldDistance = Math.max(0.0D,
                subjectDistance - requiredSeparation);
        return new Decision(true, mustYield, other.vehicleId(), selected.encounter(),
                selected.position(), subjectDistance, otherDistance,
                selected.separation(), yieldDistance);
    }

    // Check whether current travel headings approach each other.
    private static boolean directlyApproaching(
            Participant subject,
            Participant other
    ) {
        Vec3 between = horizontal(other.position().subtract(subject.position()));
        if (between.lengthSqr() <= EPSILON) return true;
        Vec3 subjectDirection = firstDirection(subject);
        Vec3 otherDirection = firstDirection(other);
        return subjectDirection.dot(between.normalize()) > 0.25D
                && otherDirection.dot(between.normalize().scale(-1.0D)) > 0.25D;
    }

    // Get one participant's immediate route direction.
    private static Vec3 firstDirection(Participant participant) {
        if (participant.route().size() < 2) return Vec3.ZERO;
        Vec3 direction = participant.route().get(1)
                .subtract(participant.route().getFirst());
        if (participant.mode() == SablePathfinder.RouteMode.GROUND) {
            direction = horizontal(direction);
        }
        return normalize(direction, participant.velocity());
    }

    // Compare stable vehicle ids without depending on registration order.
    private static int compareVehicleIds(UUID first, UUID second) {
        int high = Long.compareUnsigned(first.getMostSignificantBits(),
                second.getMostSignificantBits());
        return high != 0 ? high : Long.compareUnsigned(
                first.getLeastSignificantBits(), second.getLeastSignificantBits());
    }

    // Find the closest points on two finite line segments.
    private static SegmentApproach closestApproach(
            PathSegment first,
            PathSegment second,
            boolean ground
    ) {
        Vec3 firstStart = measured(first.start(), ground);
        Vec3 firstEnd = measured(first.end(), ground);
        Vec3 secondStart = measured(second.start(), ground);
        Vec3 secondEnd = measured(second.end(), ground);
        Vec3 firstDelta = firstEnd.subtract(firstStart);
        Vec3 secondDelta = secondEnd.subtract(secondStart);
        Vec3 offset = firstStart.subtract(secondStart);
        double a = firstDelta.dot(firstDelta);
        double e = secondDelta.dot(secondDelta);
        double f = secondDelta.dot(offset);
        double firstFraction;
        double secondFraction;
        if (a <= EPSILON && e <= EPSILON) {
            firstFraction = 0.0D;
            secondFraction = 0.0D;
        } else if (a <= EPSILON) {
            firstFraction = 0.0D;
            secondFraction = clamp(f / e, 0.0D, 1.0D);
        } else {
            double c = firstDelta.dot(offset);
            if (e <= EPSILON) {
                secondFraction = 0.0D;
                firstFraction = clamp(-c / a, 0.0D, 1.0D);
            } else {
                double b = firstDelta.dot(secondDelta);
                double denominator = a * e - b * b;
                if (Math.abs(denominator) <= EPSILON) {
                    return closestParallelApproach(
                            first, second, firstStart, firstEnd,
                            secondStart, secondEnd, firstDelta, secondDelta,
                            a, e, ground);
                }
                firstFraction = clamp(
                        (b * f - c * e) / denominator, 0.0D, 1.0D);
                secondFraction = (b * firstFraction + f) / e;
                if (secondFraction < 0.0D) {
                    secondFraction = 0.0D;
                    firstFraction = clamp(-c / a, 0.0D, 1.0D);
                } else if (secondFraction > 1.0D) {
                    secondFraction = 1.0D;
                    firstFraction = clamp((b - c) / a, 0.0D, 1.0D);
                }
            }
        }
        Vec3 firstPoint = first.start().add(
                first.end().subtract(first.start()).scale(firstFraction));
        Vec3 secondPoint = second.start().add(
                second.end().subtract(second.start()).scale(secondFraction));
        double separation = measuredDistance(firstPoint, secondPoint, ground);
        return new SegmentApproach(firstFraction, secondFraction,
                firstPoint.add(secondPoint).scale(0.5D), separation,
                Math.abs(firstPoint.y - secondPoint.y));
    }

    // Resolve parallel and overlapping segments from all endpoint projections.
    private static SegmentApproach closestParallelApproach(
            PathSegment first,
            PathSegment second,
            Vec3 firstStart,
            Vec3 firstEnd,
            Vec3 secondStart,
            Vec3 secondEnd,
            Vec3 firstDelta,
            Vec3 secondDelta,
            double firstLengthSqr,
            double secondLengthSqr,
            boolean ground
    ) {
        List<double[]> fractions = List.of(
                new double[]{0.0D, clamp(firstStart.subtract(secondStart)
                        .dot(secondDelta) / secondLengthSqr, 0.0D, 1.0D)},
                new double[]{1.0D, clamp(firstEnd.subtract(secondStart)
                        .dot(secondDelta) / secondLengthSqr, 0.0D, 1.0D)},
                new double[]{clamp(secondStart.subtract(firstStart)
                        .dot(firstDelta) / firstLengthSqr, 0.0D, 1.0D), 0.0D},
                new double[]{clamp(secondEnd.subtract(firstStart)
                        .dot(firstDelta) / firstLengthSqr, 0.0D, 1.0D), 1.0D});
        SegmentApproach selected = null;
        for (double[] pair : fractions) {
            SegmentApproach candidate = segmentApproach(
                    first, second, pair[0], pair[1], ground);
            if (selected == null
                    || candidate.separation() < selected.separation() - EPSILON
                    || Math.abs(candidate.separation() - selected.separation()) <= EPSILON
                    && candidate.firstFraction() < selected.firstFraction()) {
                selected = candidate;
            }
        }
        return selected;
    }

    // Measure one selected pair of segment fractions.
    private static SegmentApproach segmentApproach(
            PathSegment first,
            PathSegment second,
            double firstFraction,
            double secondFraction,
            boolean ground
    ) {
        Vec3 firstPoint = first.start().add(
                first.end().subtract(first.start()).scale(firstFraction));
        Vec3 secondPoint = second.start().add(
                second.end().subtract(second.start()).scale(secondFraction));
        return new SegmentApproach(firstFraction, secondFraction,
                firstPoint.add(secondPoint).scale(0.5D),
                measuredDistance(firstPoint, secondPoint, ground),
                Math.abs(firstPoint.y - secondPoint.y));
    }

    /** One vehicle's current motion and bounded future retained route. */
    public record Participant(
            UUID vehicleId,
            Vec3 position,
            Vec3 velocity,
            List<Vec3> route,
            SablePathfinder.RouteMode mode,
            double clearanceRadius,
            double verticalClearance,
            double expectedSpeed
    ) {
        public Participant {
            vehicleId = vehicleId == null ? new UUID(0L, 0L) : vehicleId;
            position = finite(position);
            velocity = finite(velocity);
            route = route == null ? List.of() : route.stream()
                    .filter(RouteTrafficPriority::finiteVector).map(RouteTrafficPriority::finite)
                    .toList();
            mode = mode == null ? SablePathfinder.RouteMode.FLIGHT : mode;
            clearanceRadius = nonNegative(clearanceRadius);
            verticalClearance = nonNegative(verticalClearance);
            expectedSpeed = nonNegative(expectedSpeed);
        }

        private double travelSpeed(double minimum) {
            return Math.max(minimum,
                    Math.max(expectedSpeed, measuredSpeed()));
        }

        private double measuredSpeed() {
            Vec3 measuredVelocity = mode == SablePathfinder.RouteMode.GROUND
                    ? horizontal(velocity) : velocity;
            return measuredVelocity.length();
        }
    }

    /** Tunable route-conflict horizon and separation policy. */
    public record Settings(
            double coordinationDistance,
            double lookaheadDistance,
            double minimumTimeSeparation,
            double clearanceBuffer,
            double minimumTravelSpeed,
            double distanceTieTolerance
    ) {
        public static final Settings DEFAULT = new Settings(
                32.0D, 32.0D, 2.0D, 1.0D, 0.25D, 0.25D);

        public Settings {
            coordinationDistance = positive(coordinationDistance, 32.0D);
            lookaheadDistance = positive(lookaheadDistance, coordinationDistance);
            minimumTimeSeparation = nonNegative(minimumTimeSeparation);
            clearanceBuffer = nonNegative(clearanceBuffer);
            minimumTravelSpeed = positive(minimumTravelSpeed, 0.25D);
            distanceTieTolerance = nonNegative(distanceTieTolerance);
        }
    }

    /** Right-of-way result; yielding changes speed only and never changes a route. */
    public record Decision(
            boolean conflict,
            boolean yield,
            UUID otherVehicleId,
            Encounter encounter,
            Vec3 conflictPosition,
            double distanceToConflict,
            double otherDistanceToConflict,
            double routeSeparation,
            double yieldDistance
    ) {
        public Decision {
            otherVehicleId = otherVehicleId == null ? new UUID(0L, 0L) : otherVehicleId;
            encounter = encounter == null ? Encounter.NONE : encounter;
            conflictPosition = finite(conflictPosition);
            distanceToConflict = nonNegative(distanceToConflict);
            otherDistanceToConflict = nonNegative(otherDistanceToConflict);
            routeSeparation = nonNegative(routeSeparation);
            yieldDistance = nonNegative(yieldDistance);
        }

        public static Decision clear() {
            return new Decision(false, false, new UUID(0L, 0L),
                    Encounter.NONE, Vec3.ZERO, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    /** Shape of the future route encounter. */
    public enum Encounter {
        NONE,
        CROSSING,
        SAME_DIRECTION,
        HEAD_ON
    }

    // Store one clipped future path.
    private record Path(List<PathSegment> segments) {
        private static Path from(Participant participant, double maximumDistance) {
            List<Vec3> points = new ArrayList<>();
            points.add(participant.position());
            for (Vec3 point : participant.route()) {
                if (points.getLast().distanceToSqr(point) > EPSILON) points.add(point);
            }
            List<PathSegment> segments = new ArrayList<>();
            double traversed = 0.0D;
            boolean ground = participant.mode() == SablePathfinder.RouteMode.GROUND;
            for (int index = 0; index + 1 < points.size()
                    && traversed < maximumDistance - EPSILON; index++) {
                Vec3 start = points.get(index);
                Vec3 end = points.get(index + 1);
                double length = measuredDistance(start, end, ground);
                if (length <= EPSILON) continue;
                double retained = Math.min(length, maximumDistance - traversed);
                Vec3 clippedEnd = retained + EPSILON >= length ? end
                        : start.add(end.subtract(start).scale(retained / length));
                segments.add(new PathSegment(start, clippedEnd,
                        normalize(measured(clippedEnd.subtract(start), ground), Vec3.ZERO),
                        retained, traversed));
                traversed += retained;
            }
            return new Path(List.copyOf(segments));
        }
    }

    private record PathSegment(
            Vec3 start,
            Vec3 end,
            Vec3 direction,
            double length,
            double distanceAtStart
    ) {
    }

    private record SegmentApproach(
            double firstFraction,
            double secondFraction,
            Vec3 midpoint,
            double separation,
            double verticalSeparation
    ) {
    }

    private record Conflict(
            Vec3 position,
            double subjectDistance,
            double otherDistance,
            double separation,
            Encounter encounter,
            boolean found
    ) {
        private static Conflict none() {
            return new Conflict(Vec3.ZERO, Double.POSITIVE_INFINITY,
                    Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                    Encounter.NONE, false);
        }
    }

    private static Vec3 measured(Vec3 value, boolean ground) {
        Vec3 safe = finite(value);
        return ground ? horizontal(safe) : safe;
    }

    private static double measuredDistance(Vec3 first, Vec3 second, boolean ground) {
        return measured(finite(first).subtract(finite(second)), ground).length();
    }

    private static Vec3 horizontal(Vec3 value) {
        Vec3 safe = finite(value);
        return new Vec3(safe.x, 0.0D, safe.z);
    }

    private static Vec3 normalize(Vec3 value, Vec3 fallback) {
        Vec3 safe = finite(value);
        if (safe.lengthSqr() > EPSILON) return safe.normalize();
        safe = finite(fallback);
        return safe.lengthSqr() > EPSILON ? safe.normalize() : Vec3.ZERO;
    }

    private static boolean finiteVector(Vec3 value) {
        return value != null && Double.isFinite(value.x)
                && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    private static Vec3 finite(Vec3 value) {
        return finiteVector(value) ? value : Vec3.ZERO;
    }

    private static double nonNegative(double value) {
        return Double.isFinite(value) ? Math.max(0.0D, value) : 0.0D;
    }

    private static double positive(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0D ? value : fallback;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
