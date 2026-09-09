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
        return search(request, true);
    }

    /** Build a bounded forward-only curve toward a live collision-avoidance target. */
    public static Plan planForwardRecovery(PoseRequest request) {
        Objects.requireNonNull(request, "request");
        return search(request, false);
    }

    /**
     * Build a forward-only bicycle path which reaches a retained route while
     * aligned with its direction of travel.
     */
    public static Plan planForwardRouteRejoin(RouteRejoinRequest request) {
        Objects.requireNonNull(request, "request");
        Vec3 start = finite(request.start());
        Vec3 routePoint = finite(request.routePosition());
        Vec3 routeDirection = horizontalUnit(
                request.routeDirection(), routePoint.subtract(start));
        Vec3 forward = horizontalUnit(request.forward(), routeDirection);
        double crossTrack = horizontal(routePoint.subtract(start)).length();
        double headingError = Math.acos(clamp(
                forward.dot(routeDirection), -1.0D, 1.0D));
        double radius = request.capabilities().minimumTurningRadius();
        double requestedAdvance = Math.max(request.stepDistance() * 4.0D,
                Math.max(crossTrack * 4.0D,
                        radius * (3.0D + Math.sin(headingError * 0.5D))));
        double routeAdvance = Math.min(
                request.maximumRouteAdvance(), requestedAdvance);
        Vec3 target = routePoint.add(routeDirection.scale(routeAdvance));
        PoseRequest movement = new PoseRequest(
                start, target, forward, request.capabilities(),
                request.searchRadius(), request.stepDistance(),
                request.maxExpansions(), request.poseValidator());
        Plan directMerge = shortestForwardPosePath(
                movement, routeDirection, Math.toRadians(12.0D));
        if (directMerge != null) return directMerge;
        return search(movement, false, routeDirection, Math.toRadians(12.0D));
    }

    // Try the four forward Dubins curve/straight/curve solutions before using the bounded search.
    private static Plan shortestForwardPosePath(
            PoseRequest request,
            Vec3 requestedCompletionHeading,
            double completionHeadingTolerance
    ) {
        Vec3 start = finite(request.start());
        Vec3 target = new Vec3(request.target().x, start.y, request.target().z);
        Vec3 startHeading = horizontalUnit(request.forward(), target.subtract(start));
        Vec3 endHeading = horizontalUnit(requestedCompletionHeading, startHeading);
        double radius = request.capabilities().minimumTurningRadius();
        double dx = (target.x - start.x) / radius;
        double dz = (target.z - start.z) / radius;
        double distance = Math.hypot(dx, dz);
        if (distance <= 1.0E-8D) return null;
        double bearing = Math.atan2(dz, dx);
        double alpha = positiveRadians(headingAngle(startHeading) - bearing);
        double beta = positiveRadians(headingAngle(endHeading) - bearing);
        List<DubinsCandidate> candidates = new ArrayList<>(4);
        addLeftStraightLeft(candidates, alpha, beta, distance);
        addRightStraightRight(candidates, alpha, beta, distance);
        addLeftStraightRight(candidates, alpha, beta, distance);
        addRightStraightLeft(candidates, alpha, beta, distance);
        candidates.sort(Comparator.comparingDouble(DubinsCandidate::normalizedLength));
        for (DubinsCandidate candidate : candidates) {
            Plan plan = buildForwardPosePath(
                    request, target, endHeading,
                    completionHeadingTolerance, candidate, radius);
            if (plan != null) return plan;
        }
        return null;
    }

    private static void addLeftStraightLeft(
            List<DubinsCandidate> candidates,
            double alpha,
            double beta,
            double distance
    ) {
        double squared = 2.0D + distance * distance
                - 2.0D * Math.cos(alpha - beta)
                + 2.0D * distance * (Math.sin(alpha) - Math.sin(beta));
        if (squared < 0.0D) return;
        double turn = Math.atan2(
                Math.cos(beta) - Math.cos(alpha),
                distance + Math.sin(alpha) - Math.sin(beta));
        candidates.add(new DubinsCandidate(
                1, positiveRadians(-alpha + turn), Math.sqrt(squared),
                1, positiveRadians(beta - turn)));
    }

    private static void addRightStraightRight(
            List<DubinsCandidate> candidates,
            double alpha,
            double beta,
            double distance
    ) {
        double squared = 2.0D + distance * distance
                - 2.0D * Math.cos(alpha - beta)
                + 2.0D * distance * (-Math.sin(alpha) + Math.sin(beta));
        if (squared < 0.0D) return;
        double turn = Math.atan2(
                Math.cos(alpha) - Math.cos(beta),
                distance - Math.sin(alpha) + Math.sin(beta));
        candidates.add(new DubinsCandidate(
                -1, positiveRadians(alpha - turn), Math.sqrt(squared),
                -1, positiveRadians(-beta + turn)));
    }

    private static void addLeftStraightRight(
            List<DubinsCandidate> candidates,
            double alpha,
            double beta,
            double distance
    ) {
        double squared = -2.0D + distance * distance
                + 2.0D * Math.cos(alpha - beta)
                + 2.0D * distance * (Math.sin(alpha) + Math.sin(beta));
        if (squared < 0.0D) return;
        double straight = Math.sqrt(squared);
        double turn = Math.atan2(
                -Math.cos(alpha) - Math.cos(beta),
                distance + Math.sin(alpha) + Math.sin(beta))
                - Math.atan2(-2.0D, straight);
        candidates.add(new DubinsCandidate(
                1, positiveRadians(-alpha + turn), straight,
                -1, positiveRadians(-beta + turn)));
    }

    private static void addRightStraightLeft(
            List<DubinsCandidate> candidates,
            double alpha,
            double beta,
            double distance
    ) {
        double squared = distance * distance - 2.0D
                + 2.0D * Math.cos(alpha - beta)
                - 2.0D * distance * (Math.sin(alpha) + Math.sin(beta));
        if (squared < 0.0D) return;
        double straight = Math.sqrt(squared);
        double turn = Math.atan2(
                Math.cos(alpha) + Math.cos(beta),
                distance - Math.sin(alpha) - Math.sin(beta))
                - Math.atan2(2.0D, straight);
        candidates.add(new DubinsCandidate(
                -1, positiveRadians(alpha - turn), straight,
                1, positiveRadians(beta - turn)));
    }

    private static Plan buildForwardPosePath(
            PoseRequest request,
            Vec3 target,
            Vec3 targetHeading,
            double headingTolerance,
            DubinsCandidate candidate,
            double radius
    ) {
        List<Curve> maneuvers = new ArrayList<>(3);
        Vec3 position = finite(request.start());
        Vec3 heading = horizontalUnit(request.forward(), target.subtract(position));
        if (candidate.firstSweep() > 1.0E-8D) {
            Curve curve = Curve.arc(position, heading, radius,
                    candidate.firstTurn() * candidate.firstSweep(), false);
            maneuvers.add(curve);
            position = curve.end();
            heading = curve.endTangent();
        }
        if (candidate.straightLength() > 1.0E-8D) {
            Curve curve = Curve.straight(position,
                    position.add(heading.scale(candidate.straightLength() * radius)), false);
            maneuvers.add(curve);
            position = curve.end();
            heading = curve.endTangent();
        }
        if (candidate.lastSweep() > 1.0E-8D) {
            Curve curve = Curve.arc(position, heading, radius,
                    candidate.lastTurn() * candidate.lastSweep(), false);
            maneuvers.add(curve);
            position = curve.end();
            heading = curve.endTangent();
        }
        double positionTolerance = Math.max(1.0E-4D, request.stepDistance() * 0.05D);
        if (position.distanceToSqr(target) > positionTolerance * positionTolerance
                || heading.dot(targetHeading) < Math.cos(Math.max(
                0.0D, finite(headingTolerance)))) {
            return null;
        }
        List<Waypoint> waypoints = new ArrayList<>();
        List<Segment> routeSegments = new ArrayList<>();
        List<Curve> curves = new ArrayList<>();
        for (Curve maneuver : maneuvers) {
            if (!appendValidatedCurve(
                    maneuver, request.stepDistance(), request.poseValidator(),
                    waypoints, routeSegments, curves)) {
                return null;
            }
        }
        return curves.isEmpty() ? null
                : new Plan(waypoints, routeSegments, curves, true, false);
    }

    private static boolean appendValidatedCurve(
            Curve curve,
            double stepDistance,
            PoseValidator validator,
            List<Waypoint> waypoints,
            List<Segment> routeSegments,
            List<Curve> curves
    ) {
        int samples = Math.max(1, (int) Math.ceil(Math.max(
                curve.length() / Math.max(0.25D, stepDistance),
                Math.abs(curve.signedSweepRadians()) / (Math.PI / 18.0D))));
        Vec3 previous = curve.start();
        for (int index = 1; index <= samples; index++) {
            double fromFraction = (index - 1) / (double) samples;
            double toFraction = index / (double) samples;
            Vec3 next = curve.pointAtFraction(toFraction);
            if (!validator.isClear(
                    new Pose(previous, curve.vehicleForwardAtFraction(fromFraction)),
                    new Pose(next, curve.vehicleForwardAtFraction(toFraction)))) {
                return false;
            }
            routeSegments.add(new Segment(previous, next, false, true));
            previous = next;
        }
        appendCurve(waypoints, curves, curve);
        return true;
    }

    private static double positiveRadians(double radians) {
        double fullTurn = Math.PI * 2.0D;
        double normalized = radians % fullTurn;
        return normalized < 0.0D ? normalized + fullTurn : normalized;
    }

    /**
     * Anticipate the next retained-route bend using the vehicle's physical minimum turning radius.
     * The returned direction changes steering only: the host keeps the certified current leg as its
     * control target and remains responsible for validating the live curved travel corridor.
     */
    public static Vec3 forwardRouteSteering(
            Vec3 position,
            Vec3 controlTarget,
            Vec3 legStart,
            Vec3 corner,
            Vec3 nextWaypoint,
            VehicleCapabilities capabilities
    ) {
        return forwardRouteControl(position, controlTarget, legStart,
                List.of(finite(corner), finite(nextWaypoint)), 0, capabilities,
                1.0D, 1.0D, 1.0D, 0.0D, 0.0D).steeringDirection();
    }

    /**
     * Look through the remaining route for its first real bend, then return the steering direction
     * and speed which can reach that bend's tangent point without overshooting it. Intermediate
     * collinear waypoints do not hide a turn. The host supplies its physical acceleration limits.
     */
    public static ForwardRouteControl forwardRouteControl(
            Vec3 position,
            Vec3 controlTarget,
            Vec3 routeOrigin,
            List<Vec3> routeWaypoints,
            int nextWaypointIndex,
            VehicleCapabilities capabilities,
            double requestedSpeed,
            double maximumLateralAcceleration,
            double brakingAcceleration,
            double responseSeconds,
            double minimumCornerSpeed
    ) {
        Vec3 current = finite(position);
        Vec3 baseline = horizontalUnit(finite(controlTarget).subtract(current), Vec3.ZERO);
        List<Vec3> route = routeWaypoints == null ? List.of() : routeWaypoints;
        double maximumSpeed = Math.max(0.0D, finite(requestedSpeed));
        int first = Math.max(0, nextWaypointIndex);
        if (first >= route.size()) return ForwardRouteControl.clear(baseline, maximumSpeed);
        Vec3 start = first == 0 ? finite(routeOrigin) : finite(route.get(first - 1));
        Vec3 corner = finite(route.get(first));
        Vec3 incomingDelta = horizontal(corner.subtract(start));
        double incomingStraight = incomingDelta.length();
        if (incomingStraight <= 1.0E-8D) {
            return ForwardRouteControl.clear(baseline, maximumSpeed);
        }
        Vec3 incoming = incomingDelta.scale(1.0D / incomingStraight);
        Vec3 projection = projectToSegment(current, start, corner);
        double distanceToCorner = horizontal(corner.subtract(projection)).length();
        VehicleCapabilities vehicle = capabilities == null
                ? new VehicleCapabilities(2.0D, Math.toRadians(30.0D),
                0.0D, 0.0D, 0.0D, true) : capabilities;
        double straightThreshold = Math.toRadians(3.0D);
        for (int index = first; index + 1 < route.size(); index++) {
            Vec3 next = finite(route.get(index + 1));
            Vec3 outgoingDelta = horizontal(next.subtract(corner));
            double outgoingLength = outgoingDelta.length();
            if (outgoingLength <= 1.0E-8D) {
                corner = next;
                continue;
            }
            Vec3 outgoing = outgoingDelta.scale(1.0D / outgoingLength);
            double turnRadians = Math.acos(clamp(incoming.dot(outgoing), -1.0D, 1.0D));
            if (turnRadians > straightThreshold) {
                double outgoingStraight = routeStraightLength(
                        route, index, outgoing, straightThreshold);
                double boundedTurn = Math.min(turnRadians, Math.toRadians(145.0D));
                double tangentDistance = vehicle.minimumTurningRadius()
                        * Math.tan(boundedTurn * 0.5D);
                tangentDistance = Math.min(incomingStraight,
                        Math.min(outgoingStraight, Math.max(0.5D, tangentDistance)));
                Vec3 steering = baseline;
                if (turnRadians < Math.toRadians(150.0D)
                        && distanceToCorner < tangentDistance) {
                    double progress = clamp(
                            1.0D - distanceToCorner / Math.max(1.0E-8D, tangentDistance),
                            0.0D, 1.0D);
                    double blend = progress * progress * (3.0D - 2.0D * progress);
                    steering = horizontalUnit(
                            baseline.scale(1.0D - blend).add(outgoing.scale(blend)), baseline);
                }
                double lateralAcceleration = Math.max(
                        1.0E-6D, finite(maximumLateralAcceleration));
                double severity = Math.max(0.05D, Math.sin(turnRadians * 0.5D));
                double effectiveRadius = vehicle.minimumTurningRadius() / severity;
                double cornerSpeed = Math.min(maximumSpeed, Math.max(
                        Math.max(0.0D, finite(minimumCornerSpeed)),
                        Math.sqrt(lateralAcceleration * effectiveRadius)));
                double availableBrakingDistance = Math.max(
                        0.0D, distanceToCorner - tangentDistance);
                double braking = Math.max(1.0E-6D, finite(brakingAcceleration));
                double response = Math.max(0.0D, finite(responseSeconds));
                double responseDistance = braking * response;
                double permittedSpeed = -responseDistance + Math.sqrt(
                        responseDistance * responseDistance
                                + cornerSpeed * cornerSpeed
                                + 2.0D * braking * availableBrakingDistance);
                return new ForwardRouteControl(steering, index, corner,
                        distanceToCorner, turnRadians, tangentDistance,
                        cornerSpeed, Math.min(maximumSpeed, permittedSpeed), true);
            }
            distanceToCorner += outgoingLength;
            incomingStraight += outgoingLength;
            corner = next;
        }
        return ForwardRouteControl.clear(baseline, maximumSpeed);
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

    private static Plan search(PoseRequest request, boolean reverse) {
        return search(request, reverse, Vec3.ZERO, 0.0D);
    }

    private static Plan search(
            PoseRequest request,
            boolean reverse,
            Vec3 requestedCompletionHeading,
            double completionHeadingTolerance
    ) {
        Vec3 start = finite(request.start());
        Vec3 requestedTarget = finite(request.target());
        Vec3 target = new Vec3(requestedTarget.x, start.y, requestedTarget.z);
        Vec3 forward = horizontalUnit(request.forward(), target.subtract(start));
        Vec3 completionHeading = horizontal(requestedCompletionHeading);
        if (completionHeading.lengthSqr() > 1.0E-8D) {
            completionHeading = completionHeading.normalize();
        }
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
        frontier.add(new QueueEntry(origin, 0.0D, routePoseHeuristic(
                start, target, forward, false, completionHeading,
                request.capabilities().minimumTurningRadius())));

        SearchNode bestSafe = null;
        double bestSafeScore = Double.POSITIVE_INFINITY;
        int expansions = 0;
        while (!frontier.isEmpty() && expansions++ < request.maxExpansions()) {
            QueueEntry entry = frontier.poll();
            State current = states.get(entry.node());
            if (current == null || entry.cost() > current.cost() + 1.0E-8D) {
                continue;
            }
            if (!closed.add(entry.node())) continue;
            if (!entry.node().equals(origin)) {
                if (entry.node().reverse() == reverse && entry.score() < bestSafeScore) {
                    bestSafe = entry.node();
                    bestSafeScore = entry.score();
                }
            }
            if (!entry.node().equals(origin)
                    && canFinish(current, target, request,
                    completionHeading, completionHeadingTolerance)) {
                // The direct final leg was collision-tested by canFinish.
                // Keep its geometry separate from the logical route endpoint:
                // callers steer between checkpoints while retaining the
                // exact arc/leg which made that endpoint reachable.
                return buildPlan(entry.node(), states, target, entry.node().reverse(), true);
            }

            for (int turn : TURN_CHOICES) {
                Transition transition = transition(
                        current, reverse, turn, step, request.capabilities());
                SearchNode next = key(start, transition.end(), transition.heading(), reverse, grid);
                if (closed.contains(next)
                        || !withinWindow(next, request.searchRadius(), grid)) {
                    continue;
                }
                if (!trace(transition, request.poseValidator())) continue;
                double moveCost = transition.length() * (reverse ? 1.28D : 1.0D)
                        + (turn == 0 ? 0.0D : step * 0.12D)
                        + (reverse && !entry.node().reverse() ? step * 0.55D : 0.0D);
                double cost = current.cost() + moveCost;
                State known = states.get(next);
                if (known != null && known.cost() <= cost + 1.0E-8D) continue;
                State accepted = new State(transition.end(), transition.heading(), cost,
                        entry.node(), transition.samples(), transition.curve(), reverse);
                states.put(next, accepted);
                frontier.add(new QueueEntry(next, cost,
                        cost + routePoseHeuristic(
                                transition.end(), target, transition.heading(), reverse,
                                completionHeading,
                                request.capabilities().minimumTurningRadius())));
            }
        }
        return bestSafe == null ? new Plan(List.of(), List.of(), false, false)
                : buildPlan(bestSafe, states, null, reverse, false);
    }

    private static boolean canFinish(
            State current,
            Vec3 target,
            PoseRequest request,
            Vec3 completionHeading,
            double completionHeadingTolerance
    ) {
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
        if (completionHeading.lengthSqr() > 1.0E-8D
                && targetHeading.dot(completionHeading) < Math.cos(Math.max(
                0.0D, finite(completionHeadingTolerance)))) {
            return false;
        }
        return request.poseValidator().isClear(
                new Pose(current.position(), current.heading()),
                new Pose(target, completionHeading.lengthSqr() > 1.0E-8D
                        ? completionHeading : targetHeading));
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
            // sampled pieces are retained above for collision geometry,
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

    // Favor the route tangent early enough for a forward-only pose merge.
    private static double routePoseHeuristic(
            Vec3 position,
            Vec3 target,
            Vec3 heading,
            boolean reverse,
            Vec3 completionHeading,
            double turningRadius
    ) {
        double score = heuristic(position, target, heading, reverse);
        if (completionHeading.lengthSqr() <= 1.0E-8D) return score;
        Vec3 offset = horizontal(target.subtract(position));
        Vec3 routeLeft = new Vec3(
                -completionHeading.z, 0.0D, completionHeading.x);
        double lateralOffset = offset.dot(routeLeft);
        double crossTrack = Math.abs(lateralOffset);
        Vec3 travel = horizontalUnit(
                heading.scale(reverse ? -1.0D : 1.0D), completionHeading);
        double approachRun = Math.max(
                Math.max(0.35D, turningRadius) * 2.0D,
                Math.max(0.0D, offset.dot(completionHeading)) * 0.4D);
        Vec3 approachHeading = horizontalUnit(
                completionHeading.scale(approachRun)
                        .add(routeLeft.scale(lateralOffset)),
                completionHeading);
        double approachPenalty = 1.0D - clamp(
                travel.dot(approachHeading), -1.0D, 1.0D);
        double finalHeadingPenalty = 1.0D - clamp(
                travel.dot(completionHeading), -1.0D, 1.0D);
        double finalHeadingWeight = clamp(
                1.0D - crossTrack / Math.max(1.0D, turningRadius * 2.0D),
                0.0D, 1.0D);
        double passedTarget = Math.max(
                0.0D, -offset.dot(completionHeading));
        return score + crossTrack * 3.0D
                + Math.max(0.35D, turningRadius) * approachPenalty * 4.0D
                + Math.max(0.35D, turningRadius) * finalHeadingPenalty
                * finalHeadingWeight * 4.0D
                + passedTarget * 4.0D;
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

    private static Vec3 projectToSegment(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 segment = end.subtract(start);
        double lengthSqr = segment.lengthSqr();
        if (lengthSqr <= 1.0E-12D) return end;
        double progress = clamp(point.subtract(start).dot(segment) / lengthSqr,
                0.0D, 1.0D);
        return start.add(segment.scale(progress));
    }

    // Measure the usable straight after one corner without crossing the next real bend.
    private static double routeStraightLength(
            List<Vec3> route,
            int cornerIndex,
            Vec3 direction,
            double straightThreshold
    ) {
        double length = 0.0D;
        Vec3 previous = finite(route.get(cornerIndex));
        for (int index = cornerIndex + 1; index < route.size(); index++) {
            Vec3 next = finite(route.get(index));
            Vec3 delta = horizontal(next.subtract(previous));
            double segmentLength = delta.length();
            if (segmentLength <= 1.0E-8D) {
                previous = next;
                continue;
            }
            Vec3 segmentDirection = delta.scale(1.0D / segmentLength);
            double turn = Math.acos(clamp(
                    direction.dot(segmentDirection), -1.0D, 1.0D));
            if (turn > straightThreshold) break;
            length += segmentLength;
            previous = next;
        }
        return length;
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

    /** Reusable steering and braking result for the first real bend ahead. */
    public record ForwardRouteControl(
            Vec3 steeringDirection,
            int cornerWaypointIndex,
            Vec3 corner,
            double distanceToCorner,
            double turnRadians,
            double tangentDistance,
            double cornerSpeed,
            double permittedSpeed,
            boolean turnAhead
    ) {
        public ForwardRouteControl {
            steeringDirection = horizontalUnit(steeringDirection, Vec3.ZERO);
            cornerWaypointIndex = Math.max(-1, cornerWaypointIndex);
            corner = finite(corner);
            distanceToCorner = Double.isFinite(distanceToCorner)
                    ? Math.max(0.0D, distanceToCorner) : Double.POSITIVE_INFINITY;
            turnRadians = clamp(Math.abs(finite(turnRadians)), 0.0D, Math.PI);
            tangentDistance = Math.max(0.0D, finite(tangentDistance));
            cornerSpeed = Math.max(0.0D, finite(cornerSpeed));
            permittedSpeed = Math.max(0.0D, finite(permittedSpeed));
        }

        // Return unconstrained straight-route control.
        public static ForwardRouteControl clear(Vec3 steeringDirection, double speed) {
            double permitted = Math.max(0.0D, finite(speed));
            return new ForwardRouteControl(steeringDirection, -1, Vec3.ZERO,
                    Double.POSITIVE_INFINITY, 0.0D, 0.0D,
                    permitted, permitted, false);
        }
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

    /** Inputs for a direction-aligned retained-route rejoin. */
    public record RouteRejoinRequest(
            Vec3 start,
            Vec3 forward,
            Vec3 routePosition,
            Vec3 routeDirection,
            double maximumRouteAdvance,
            VehicleCapabilities capabilities,
            double searchRadius,
            double stepDistance,
            int maxExpansions,
            PoseValidator poseValidator
    ) {
        public RouteRejoinRequest {
            start = finite(start);
            forward = horizontalUnit(forward, new Vec3(1.0D, 0.0D, 0.0D));
            routePosition = finite(routePosition);
            routeDirection = horizontalUnit(
                    routeDirection, routePosition.subtract(start));
            maximumRouteAdvance = Double.isFinite(maximumRouteAdvance)
                    ? Math.max(0.0D, maximumRouteAdvance) : Double.MAX_VALUE;
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
    private record DubinsCandidate(
            int firstTurn,
            double firstSweep,
            double straightLength,
            int lastTurn,
            double lastSweep
    ) {
        private double normalizedLength() {
            return firstSweep + straightLength + lastSweep;
        }
    }

    private static double finite(double value) { return Double.isFinite(value) ? value : 0.0D; }
    private static double finitePositive(double value, double fallback) { return Double.isFinite(value) && value > 0.0D ? value : fallback; }
}
