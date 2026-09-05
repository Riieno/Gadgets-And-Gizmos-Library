package com.rieno.gadgetsandgizmos.lib.navigation;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Bounded reverse recovery planning for blocked steering vehicles.
 *
 * <p>The planner uses a bicycle model: every move is a sampled reverse arc
 * whose radius comes from the wheelbase and steering limit. This
 * prevents consumers from treating a car as a point which can turn in place
 * or move sideways. World, collision, wheel and suspension data remain
 * host-owned and are supplied through {@link PoseValidator}.</p>
 */
public final class GroundPathPlanner {
    private static final int HEADING_STEPS = 16;
    private static final int[] TURN_CHOICES = {-1, 0, 1};
    private GroundPathPlanner() {
    }

    /** Build a bounded reverse-only recovery route for a blocked steering vehicle. */
    public static Plan planReverseRecovery(PoseRequest request) {
        Objects.requireNonNull(request, "request");
        if (!request.capabilities().allowReverse()) return Plan.empty();
        return search(request);
    }

    /**
     * Return whether a cached reverse manoeuvre should be replaced by direct
     * forward travel. The host supplies clearance from its own full-hull probe.
     */
    public static boolean shouldRefreshReverseRoute(
            Vec3 vehicleForward,
            Vec3 targetDirection,
            double forwardClearance,
            double requiredClearance
    ) {
        Vec3 forward = horizontalUnit(vehicleForward, new Vec3(1.0D, 0.0D, 0.0D));
        Vec3 target = horizontalUnit(targetDirection, forward);
        double clearance = Math.max(0.0D, finite(forwardClearance));
        double required = Math.max(0.0D, finite(requiredClearance));
        return forward.dot(target) >= 0.35D && clearance >= required;
    }

    /**
     * Return whether forward travel can resume after the vehicle has completed
     * the minimum useful part of its reverse manoeuvre.
     */
    public static boolean shouldRefreshReverseRoute(
            Vec3 vehicleForward,
            Vec3 targetDirection,
            double forwardClearance,
            double requiredClearance,
            double reversedDistance,
            double minimumReverseDistance
    ) {
        if (Math.max(0.0D, finite(reversedDistance))
                < Math.max(0.0D, finite(minimumReverseDistance))) {
            return false;
        }
        return shouldRefreshReverseRoute(
                vehicleForward, targetDirection,
                forwardClearance, requiredClearance);
    }

    private static Plan search(PoseRequest request) {
        Vec3 start = finite(request.start());
        Vec3 requestedTarget = finite(request.target());
        Vec3 target = new Vec3(requestedTarget.x, start.y, requestedTarget.z);
        Vec3 forward = horizontalUnit(request.forward(), target.subtract(start));
        if (target.subtract(start).lengthSqr() <= 1.0E-8D) {
            return Plan.empty();
        }

        double step = request.stepDistance();
        double grid = Math.max(0.5D, step * 0.5D);
        SearchNode origin = new SearchNode(0, 0, headingIndex(forward), false);
        Map<SearchNode, State> states = new HashMap<>();
        Set<SearchNode> closed = new HashSet<>();
        PriorityQueue<QueueEntry> frontier = new PriorityQueue<>(Comparator.comparingDouble(QueueEntry::score));
        State startState = new State(start, forward, 0.0D, null, List.of(), null, false);
        states.put(origin, startState);
        frontier.add(new QueueEntry(origin, 0.0D, heuristic(start, target, forward, false)));

        SearchNode bestReverseSafe = null;
        double bestReverseSafeScore = Double.POSITIVE_INFINITY;
        int expansions = 0;
        while (!frontier.isEmpty() && expansions++ < request.maxExpansions()) {
            QueueEntry entry = frontier.poll();
            State current = states.get(entry.node());
            if (current == null || entry.cost() > current.cost() + 1.0E-8D) {
                continue;
            }
            if (!closed.add(entry.node())) continue;
            if (!entry.node().equals(origin)) {
                if (entry.node().reverse() && entry.score() < bestReverseSafeScore) {
                    bestReverseSafe = entry.node();
                    bestReverseSafeScore = entry.score();
                }
            }
            if (!entry.node().equals(origin)
                    && canFinish(current, target, request)) {
                // The direct final leg was collision-tested by canFinish.
                // Keep its geometry separate from the logical route endpoint:
                // callers steer between checkpoints while renderers can show
                // the exact arc/leg which made that endpoint reachable.
                return buildPlan(entry.node(), states, target, entry.node().reverse(), true);
            }

            for (int turn : TURN_CHOICES) {
                Transition transition = transition(
                        current, true, turn, step, request.capabilities());
                SearchNode next = key(start, transition.end(), transition.heading(), true, grid);
                if (closed.contains(next)
                        || !withinWindow(next, request.searchRadius(), grid)) {
                    continue;
                }
                if (!trace(transition, request.poseValidator())) continue;
                double moveCost = transition.length() * 1.28D
                        + (turn == 0 ? 0.0D : step * 0.12D)
                        + (!entry.node().reverse() ? step * 0.55D : 0.0D);
                double cost = current.cost() + moveCost;
                State known = states.get(next);
                if (known != null && known.cost() <= cost + 1.0E-8D) continue;
                State accepted = new State(transition.end(), transition.heading(), cost,
                        entry.node(), transition.samples(), transition.curve(), true);
                states.put(next, accepted);
                frontier.add(new QueueEntry(next, cost,
                        cost + heuristic(transition.end(), target, transition.heading(), true)));
            }
        }
        return bestReverseSafe == null ? new Plan(List.of(), List.of(), false, false)
                : buildPlan(bestReverseSafe, states, null, true, false);
    }

    private static boolean canFinish(State current, Vec3 target, PoseRequest request) {
        Vec3 toGoal = target.subtract(current.position());
        double distance = toGoal.length();
        if (distance <= 1.0E-8D) return true;
        Vec3 travel = current.heading().scale(current.reverse() ? -1.0D : 1.0D);
        // Collision-free is not enough: a car cannot follow an arbitrary
        // diagonal chord from its current pose. Only accept the final straight
        // two-point maneuver once its physical travel tangent is already near
        // that chord. Otherwise keep searching for a radius-valid arc (or a
        // reverse recovery) instead of sending the controller into circles.
        double alignment = travel.dot(toGoal.scale(1.0D / distance));
        double finishDistance = Math.max(request.stepDistance() * 1.5D,
                request.capabilities().minimumTurningRadius() * 0.8D);
        double requiredAlignment = distance > finishDistance ? 0.995D
                : distance <= request.stepDistance() * 0.5D ? 0.80D : 0.96D;
        if (alignment < requiredAlignment) {
            return false;
        }
        Vec3 targetHeading = horizontalUnit(toGoal, current.heading())
                .scale(current.reverse() ? -1.0D : 1.0D);
        return request.poseValidator().isClear(
                new Pose(current.position(), current.heading()),
                new Pose(target, targetHeading));
    }

    private static Plan buildPlan(
            SearchNode terminal,
            Map<SearchNode, State> states,
            Vec3 completionTarget,
            boolean completionReverse,
            boolean reachesGoal
    ) {
        ArrayDeque<State> reversed = new ArrayDeque<>();
        SearchNode cursor = terminal;
        while (cursor != null) {
            State state = states.get(cursor);
            if (state == null || state.parent() == null) {
                break;
            }
            reversed.addFirst(state);
            cursor = state.parent();
        }
        List<Waypoint> path = new ArrayList<>();
        List<Segment> routeSegments = new ArrayList<>();
        List<Curve> curves = new ArrayList<>();
        for (State state : reversed) {
            List<Sample> samples = state.samples();
            if (samples.isEmpty()) {
                continue;
            }
            for (Sample sample : samples) {
                routeSegments.add(new Segment(sample.from(), sample.to(), sample.reverse(), true));
            }
            // One steering maneuver produces one control checkpoint. The
            // sampled pieces are retained above for collision/debug geometry,
            // never as instructions to stop at each point of an arc.
            appendCurve(path, curves, state.curve());
        }
        if (completionTarget != null
                && (path.isEmpty() || path.getLast().position().distanceToSqr(completionTarget) > 1.0E-8D)) {
            Vec3 start = path.isEmpty() ? states.get(terminal).position() : path.getLast().position();
            routeSegments.add(new Segment(start, completionTarget, completionReverse, true));
            appendCurve(path, curves,
                    Curve.straight(start, completionTarget, completionReverse));
        }
        return new Plan(List.copyOf(path), List.copyOf(routeSegments), List.copyOf(curves),
                reachesGoal, !reachesGoal && !path.isEmpty());
    }

    // Collapse adjacent pieces of one physical manoeuvre into one public checkpoint.
    private static void appendCurve(
            List<Waypoint> path,
            List<Curve> curves,
            Curve next
    ) {
        if (next == null || next.length() <= 1.0E-8D) return;
        if (!curves.isEmpty()) {
            Curve merged = mergeCurves(curves.getLast(), next);
            if (merged != null) {
                curves.set(curves.size() - 1, merged);
                path.set(path.size() - 1,
                        new Waypoint(merged.end(), merged.reverse()));
                return;
            }
        }
        curves.add(next);
        path.add(new Waypoint(next.end(), next.reverse()));
    }

    // Merge only geometry which retains the exact same gear and steering input.
    private static Curve mergeCurves(Curve first, Curve second) {
        if (first.reverse() != second.reverse()
                || first.end().distanceToSqr(second.start()) > 1.0E-8D) {
            return null;
        }
        if (!first.isArc() && !second.isArc()
                && first.endTangent().dot(second.startTangent()) >= 0.9995D) {
            return Curve.straight(first.start(), second.end(), first.reverse());
        }
        double sweep = first.signedSweepRadians() + second.signedSweepRadians();
        if (first.isArc() && second.isArc()
                && Math.signum(first.signedSweepRadians())
                == Math.signum(second.signedSweepRadians())
                && Math.abs(sweep) <= Math.PI
                && first.center().distanceToSqr(second.center()) <= 1.0E-8D) {
            return new Curve(first.start(), second.end(), first.center(),
                    first.startTangent(), second.endTangent(), sweep,
                    first.reverse());
        }
        return null;
    }

    private static Transition transition(State current, boolean reverse, int turn,
                                         double distance, VehicleCapabilities capabilities) {
        double direction = reverse ? -1.0D : 1.0D;
        double radius = capabilities.minimumTurningRadius();
        double headingDelta = turn == 0 ? 0.0D : direction * turn * distance / radius;
        Vec3 travelStart = current.heading().scale(direction);
        Curve curve = turn == 0
                ? Curve.straight(current.position(),
                current.position().add(travelStart.scale(distance)), reverse)
                : Curve.arc(current.position(), travelStart, radius, headingDelta, reverse);
        int samples = Math.max(1, (int) Math.ceil(Math.abs(headingDelta) / (Math.PI / 18.0D)));
        List<Sample> trace = new ArrayList<>(samples);
        Vec3 previous = current.position();
        for (int index = 1; index <= samples; index++) {
            double fraction = index / (double) samples;
            Vec3 next = curve.pointAtFraction(fraction);
            trace.add(new Sample(previous, next, reverse));
            previous = next;
        }
        return new Transition(previous, directionVector(headingAngle(current.heading()) + headingDelta),
                List.copyOf(trace), distance, curve);
    }

    private static boolean trace(Transition transition, PoseValidator validator) {
        Curve curve = transition.curve();
        for (Sample sample : transition.samples()) {
            double fromProgress = curve.nearestFraction(sample.from());
            double toProgress = curve.nearestFraction(sample.to());
            if (!validator.isClear(
                    new Pose(sample.from(), curve.vehicleForwardAtFraction(fromProgress)),
                    new Pose(sample.to(), curve.vehicleForwardAtFraction(toProgress)))) {
                return false;
            }
        }
        return true;
    }

    private static SearchNode key(Vec3 origin, Vec3 position, Vec3 heading, boolean reverse, double grid) {
        return new SearchNode((int) Math.round((position.x - origin.x) / grid),
                (int) Math.round((position.z - origin.z) / grid), headingIndex(heading), reverse);
    }

    private static boolean withinWindow(SearchNode node, double radius, double grid) {
        return Math.hypot(node.x() * grid, node.z() * grid) <= radius + grid;
    }

    private static double heuristic(Vec3 position, Vec3 target, Vec3 heading, boolean reverse) {
        return position.distanceTo(target) + recoveryScore(position, target, heading, reverse) * 0.25D;
    }

    private static double recoveryScore(Vec3 position, Vec3 target, Vec3 heading, boolean reverse) {
        Vec3 toGoal = target.subtract(position);
        if (toGoal.lengthSqr() <= 1.0E-8D) return 0.0D;
        Vec3 travel = heading.scale(reverse ? -1.0D : 1.0D);
        return toGoal.length() + (1.0D - clamp(travel.dot(horizontalUnit(toGoal, heading)), -1.0D, 1.0D)) * 3.0D;
    }

    private static int headingIndex(Vec3 vector) {
        return Math.floorMod((int) Math.round(headingAngle(vector) * HEADING_STEPS / (Math.PI * 2.0D)), HEADING_STEPS);
    }

    private static double headingAngle(Vec3 vector) {
        return Math.atan2(vector.z, vector.x);
    }

    private static Vec3 directionVector(double radians) {
        return new Vec3(Math.cos(radians), 0.0D, Math.sin(radians));
    }

    private static Vec3 horizontal(Vec3 value) {
        return value == null || !Double.isFinite(value.x) || !Double.isFinite(value.z)
                ? Vec3.ZERO : new Vec3(value.x, 0.0D, value.z);
    }

    private static Vec3 finite(Vec3 value) {
        return value == null || !Double.isFinite(value.x) || !Double.isFinite(value.y) || !Double.isFinite(value.z)
                ? Vec3.ZERO : value;
    }

    private static Vec3 horizontalUnit(Vec3 value, Vec3 fallback) {
        Vec3 horizontal = horizontal(value);
        if (horizontal.lengthSqr() > 1.0E-8D) return horizontal.normalize();
        horizontal = horizontal(fallback);
        return horizontal.lengthSqr() > 1.0E-8D ? horizontal.normalize() : new Vec3(1.0D, 0.0D, 0.0D);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /** Vehicle geometry and terrain capability used by the kinematic planner. */
    public record VehicleCapabilities(double wheelbase, double maximumSteeringRadians,
                                      double bodyClearance, double suspensionTravel,
                                      double maximumStepHeight, boolean allowReverse) {
        public VehicleCapabilities {
            wheelbase = finitePositive(wheelbase, 2.0D);
            maximumSteeringRadians = clamp(Math.abs(maximumSteeringRadians), Math.toRadians(3.0D), Math.toRadians(70.0D));
            bodyClearance = Math.max(0.0D, finite(bodyClearance));
            suspensionTravel = Math.max(0.0D, finite(suspensionTravel));
            maximumStepHeight = Math.max(0.0D, finite(maximumStepHeight));
        }

        public double minimumTurningRadius() {
            return Math.max(0.35D, wheelbase / Math.tan(maximumSteeringRadians));
        }
    }

    // Store a pose-aware ground route request
    public record PoseRequest(Vec3 start, Vec3 target, Vec3 forward,
                              VehicleCapabilities capabilities,
                              double searchRadius, double stepDistance,
                              int maxExpansions, PoseValidator poseValidator) {
        // Initialize the pose-aware ground route request
        public PoseRequest {
            start = start == null ? Vec3.ZERO : start;
            target = target == null ? Vec3.ZERO : target;
            forward = forward == null ? new Vec3(1.0D, 0.0D, 0.0D) : forward;
            capabilities = capabilities == null
                    ? new VehicleCapabilities(2.0D, Math.toRadians(30.0D),
                    0.0D, 0.0D, 0.0D, true) : capabilities;
            searchRadius = finitePositive(searchRadius, 16.0D);
            stepDistance = finitePositive(stepDistance, 1.0D);
            maxExpansions = Math.max(1, maxExpansions);
            poseValidator = Objects.requireNonNull(poseValidator, "poseValidator");
        }
    }

    // Validate the complete ground vehicle sweep between two poses
    @FunctionalInterface
    public interface PoseValidator {
        // Check whether the vehicle can traverse between two poses
        boolean isClear(Pose from, Pose to);
    }

    // Store one ground vehicle pose
    public record Pose(Vec3 position, Vec3 forward) {
        // Initialize the ground vehicle pose
        public Pose {
            position = finite(position);
            forward = horizontalUnit(forward, new Vec3(1.0D, 0.0D, 0.0D));
        }
    }

    /** A logical control checkpoint at the end of a collision-tested maneuver. */
    public record Waypoint(Vec3 position, boolean reverse) {
        public Waypoint { position = position == null ? Vec3.ZERO : position; }
    }

    /**
     * A real two-point steering maneuver. The endpoints are the only route
     * checkpoints; the tangent/radius describe the bicycle-model arc the host
     * must follow between them. Straight maneuvers have a zero sweep.
     */
    public record Curve(
            Vec3 start,
            Vec3 end,
            Vec3 center,
            Vec3 startTangent,
            Vec3 endTangent,
            double signedSweepRadians,
            boolean reverse
    ) {
        public Curve {
            start = finite(start);
            end = finite(end);
            center = finite(center);
            startTangent = horizontalUnit(startTangent, end.subtract(start));
            endTangent = horizontalUnit(endTangent, startTangent);
            signedSweepRadians = Double.isFinite(signedSweepRadians)
                    ? signedSweepRadians : 0.0D;
        }

        /** Create a straight two-point maneuver. */
        public static Curve straight(Vec3 start, Vec3 end, boolean reverse) {
            Vec3 tangent = horizontalUnit(finite(end).subtract(finite(start)), new Vec3(1.0D, 0.0D, 0.0D));
            return new Curve(start, end, Vec3.ZERO, tangent, tangent, 0.0D, reverse);
        }

        /** Create a capability-derived circular bicycle maneuver. */
        public static Curve arc(
                Vec3 start,
                Vec3 travelStart,
                double radius,
                double signedSweepRadians,
                boolean reverse
        ) {
            Vec3 origin = finite(start);
            Vec3 tangent = horizontalUnit(travelStart, new Vec3(1.0D, 0.0D, 0.0D));
            double safeRadius = finitePositive(radius, 0.35D);
            double sweep = Double.isFinite(signedSweepRadians) ? signedSweepRadians : 0.0D;
            if (Math.abs(sweep) <= 1.0E-8D) {
                return straight(origin, origin.add(tangent.scale(safeRadius * Math.abs(sweep))), reverse);
            }
            Vec3 left = new Vec3(-tangent.z, 0.0D, tangent.x);
            Vec3 center = origin.add(left.scale(Math.copySign(safeRadius, sweep)));
            Vec3 radial = origin.subtract(center);
            Vec3 end = center.add(rotateHorizontal(radial, sweep));
            Vec3 endTangent = rotateHorizontal(tangent, sweep);
            return new Curve(origin, end, center, tangent, endTangent, sweep, reverse);
        }

        /** Whether this maneuver is a circular arc rather than a line. */
        public boolean isArc() {
            return Math.abs(signedSweepRadians) > 1.0E-8D
                    && start.distanceToSqr(center) > 1.0E-8D;
        }

        /** Physical arc length used to advance a continuous steering target. */
        public double length() {
            return isArc()
                    ? start.distanceTo(center) * Math.abs(signedSweepRadians)
                    : start.distanceTo(end);
        }

        /** Get a point along this real curve without adding a route checkpoint. */
        public Vec3 pointAtFraction(double fraction) {
            double progress = clamp(fraction, 0.0D, 1.0D);
            if (!isArc()) {
                return start.lerp(end, progress);
            }
            return center.add(rotateHorizontal(start.subtract(center),
                    signedSweepRadians * progress));
        }

        /** Get the travel tangent at a point along this real curve. */
        public Vec3 tangentAtFraction(double fraction) {
            return isArc()
                    ? horizontalUnit(rotateHorizontal(startTangent,
                    signedSweepRadians * clamp(fraction, 0.0D, 1.0D)), startTangent)
                    : startTangent;
        }

        /** Get the physical vehicle-forward axis along this maneuver. */
        public Vec3 vehicleForwardAtFraction(double fraction) {
            Vec3 travel = tangentAtFraction(fraction);
            return reverse ? travel.scale(-1.0D) : travel;
        }

        // Get the vehicle pose at one point along the curve
        public Pose poseAtFraction(double fraction) {
            return new Pose(pointAtFraction(fraction), vehicleForwardAtFraction(fraction));
        }

        /** Project a world point onto the finite curve as a [0, 1] progress. */
        public double nearestFraction(Vec3 position) {
            Vec3 point = finite(position);
            if (!isArc()) {
                Vec3 chord = end.subtract(start);
                double lengthSquared = chord.lengthSqr();
                return lengthSquared <= 1.0E-12D ? 1.0D
                        : clamp(point.subtract(start).dot(chord) / lengthSquared, 0.0D, 1.0D);
            }
            Vec3 radial = new Vec3(point.x - center.x, 0.0D, point.z - center.z);
            if (radial.lengthSqr() <= 1.0E-12D) return 0.0D;
            Vec3 startRadial = start.subtract(center);
            double startAngle = Math.atan2(startRadial.z, startRadial.x);
            double pointAngle = Math.atan2(radial.z, radial.x);
            double delta = wrapRadians(pointAngle - startAngle);
            if (signedSweepRadians > 0.0D && delta < 0.0D) delta += Math.PI * 2.0D;
            if (signedSweepRadians < 0.0D && delta > 0.0D) delta -= Math.PI * 2.0D;
            return clamp(delta / signedSweepRadians, 0.0D, 1.0D);
        }

        private static Vec3 rotateHorizontal(Vec3 value, double radians) {
            double cosine = Math.cos(radians);
            double sine = Math.sin(radians);
            return new Vec3(value.x * cosine - value.z * sine, value.y,
                    value.x * sine + value.z * cosine);
        }

        private static double wrapRadians(double radians) {
            return Math.atan2(Math.sin(radians), Math.cos(radians));
        }
    }

    /** A collision-tested curve sample suitable only for rendering/inspection. */
    public record Segment(Vec3 from, Vec3 to, boolean reverse, boolean clear) {
        public Segment {
            from = from == null ? Vec3.ZERO : from;
            to = to == null ? Vec3.ZERO : to;
        }
    }

    /** Result containing sparse checkpoints and real capability-derived curves. */
    public record Plan(List<Waypoint> waypoints, List<Segment> routeSegments,
                       List<Curve> curves, boolean reachesGoal, boolean partial) {
        public Plan {
            waypoints = waypoints == null ? List.of() : List.copyOf(waypoints);
            routeSegments = routeSegments == null ? List.of() : List.copyOf(routeSegments);
            curves = curves == null ? List.of() : List.copyOf(curves);
        }

        /** Preserve the original public constructor for existing hosts. */
        public Plan(List<Waypoint> waypoints, List<Segment> routeSegments,
                    boolean reachesGoal, boolean partial) {
            this(waypoints, routeSegments, List.of(), reachesGoal, partial);
        }

        public static Plan empty() { return new Plan(List.of(), List.of(), List.of(), true, false); }
    }

    private record SearchNode(int x, int z, int heading, boolean reverse) { }
    private record State(Vec3 position, Vec3 heading, double cost, SearchNode parent,
                         List<Sample> samples, Curve curve, boolean reverse) { }
    private record QueueEntry(SearchNode node, double cost, double score) { }
    private record Sample(Vec3 from, Vec3 to, boolean reverse) { }
    private record Transition(Vec3 end, Vec3 heading, List<Sample> samples,
                              double length, Curve curve) { }

    private static double finite(double value) { return Double.isFinite(value) ? value : 0.0D; }
    private static double finitePositive(double value, double fallback) { return Double.isFinite(value) && value > 0.0D ? value : fallback; }
}
