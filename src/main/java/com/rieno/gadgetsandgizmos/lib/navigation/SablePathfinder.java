package com.rieno.gadgetsandgizmos.lib.navigation;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi;
import com.rieno.gadgetsandgizmos.lib.physics.SubLevelParticleOcclusion;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;

// Find bounded, collision-safe world routes through the root level and live Sable sublevels
public final class SablePathfinder {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final double EPSILON = 1.0E-6D;
    private static final int MAX_DEBUG_CHECKED_SEGMENTS = 128;
    // Completion-time shortcutting is cosmetic and must not become an unbounded collision burst.
    private static final int MAX_COMPLETED_ROUTE_SHORTCUT_VALIDATIONS = 128;
    private static final int MAX_ROUTE_GRAPH_BRANCH_SEARCH = 65_536;
    private static final List<GridStep> SPATIAL_STEPS = spatialSteps();
    private static final List<GridStep> PLANAR_STEPS = planarSteps();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the Sable pathfinder
    private SablePathfinder() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Find one bounded safe route using a caller-owned traversal policy
    public static Result plan(Request request) {
        QueuedPlan queued = queue(request);
        queued.advance(request.maximumExpansions());
        return queued.result();
    }

    // Create a retained, bounded planner which callers may advance over multiple server ticks.
    public static QueuedPlan queue(Request request) {
        return new QueuedPlan(Objects.requireNonNull(request, "request"));
    }

    // Advance a route cursor when its swept hull has already crossed one or more checkpoints.
    public static WaypointAdvance advanceWaypointOverlap(
            List<Vec3> waypoints,
            int nextWaypointIndex,
            Vec3 previousPosition,
            Vec3 currentPosition,
            Safety safety,
            double capturePadding
    ) {
        List<Vec3> route = waypoints == null ? List.of() : waypoints;
        int index = Math.max(0, Math.min(nextWaypointIndex, route.size()));
        Vec3 current = finite(currentPosition) ? currentPosition : Vec3.ZERO;
        Vec3 previous = finite(previousPosition) ? previousPosition : current;
        Safety envelope = safety == null ? Safety.DEFAULT : safety;
        double padding = nonNegative(capturePadding, 0.0D);
        AABB sweptHull = envelope.boundsAt(previous).minmax(envelope.boundsAt(current));
        return advanceWaypointOverlap(waypoints, nextWaypointIndex, previous, current, padding,
                (start, end, waypoint, overlapPadding) -> sweptHull.inflate(overlapPadding)
                        .contains(waypoint));
    }

    // Advance a route cursor using the consumer's exact swept-hull overlap test.
    // This supports non-box hulls without coupling the pathfinder to a host's physics model.
    public static WaypointAdvance advanceWaypointOverlap(
            List<Vec3> waypoints,
            int nextWaypointIndex,
            Vec3 previousPosition,
            Vec3 currentPosition,
            double capturePadding,
            WaypointOverlap overlap
    ) {
        List<Vec3> route = waypoints == null ? List.of() : waypoints;
        int index = Math.max(0, Math.min(nextWaypointIndex, route.size()));
        Vec3 current = finite(currentPosition) ? currentPosition : Vec3.ZERO;
        Vec3 previous = finite(previousPosition) ? previousPosition : current;
        double padding = nonNegative(capturePadding, 0.0D);
        if (overlap == null) return new WaypointAdvance(index, 0);
        int first = index;
        while (index < route.size()) {
            Vec3 waypoint = route.get(index);
            if (!finite(waypoint) || !overlap.overlaps(previous, current, waypoint, padding)) break;
            index++;
        }
        return new WaypointAdvance(index, index - first);
    }

    // Advance to the endpoint of the furthest contiguous route leg crossed by a swept safety envelope.
    public static WaypointAdvance advanceRouteOverlap(
            List<Vec3> waypoints,
            int nextWaypointIndex,
            Vec3 routeOrigin,
            Vec3 previousPosition,
            Vec3 currentPosition,
            Safety safety,
            double capturePadding
    ) {
        Vec3 current = finite(currentPosition) ? currentPosition : Vec3.ZERO;
        Vec3 previous = finite(previousPosition) ? previousPosition : current;
        Safety envelope = safety == null ? Safety.DEFAULT : safety;
        double padding = nonNegative(capturePadding, 0.0D);
        AABB sweptHull = envelope.boundsAt(previous).minmax(envelope.boundsAt(current));
        return advanceRouteOverlap(waypoints, nextWaypointIndex, routeOrigin,
                previous, current, padding,
                (movementStart, movementEnd, legStart, legEnd, overlapPadding) ->
                        segmentIntersects(sweptHull.inflate(overlapPadding), legStart, legEnd));
    }

    // Advance a route cursor using the consumer's exact swept-hull/route-leg overlap test.
    public static WaypointAdvance advanceRouteOverlap(
            List<Vec3> waypoints,
            int nextWaypointIndex,
            Vec3 routeOrigin,
            Vec3 previousPosition,
            Vec3 currentPosition,
            double capturePadding,
            RouteLegOverlap overlap
    ) {
        List<Vec3> route = waypoints == null ? List.of() : waypoints;
        int index = Math.max(0, Math.min(nextWaypointIndex, route.size()));
        Vec3 current = finite(currentPosition) ? currentPosition : Vec3.ZERO;
        Vec3 previous = finite(previousPosition) ? previousPosition : current;
        double padding = nonNegative(capturePadding, 0.0D);
        if (overlap == null || index >= route.size()) {
            return new WaypointAdvance(index, 0);
        }
        int first = index;
        while (index < route.size()) {
            Vec3 waypoint = route.get(index);
            if (!finite(waypoint)
                    || !overlap.overlaps(previous, current, waypoint, waypoint, padding)) {
                break;
            }
            index++;
        }
        if (index >= route.size()) {
            return new WaypointAdvance(index, index - first);
        }

        Vec3 legStart = index == 0
                ? finite(routeOrigin) ? routeOrigin : current
                : route.get(index - 1);
        int furthestOverlappedLeg = -1;
        for (int legIndex = index; legIndex < route.size(); legIndex++) {
            Vec3 legEnd = route.get(legIndex);
            if (!finite(legStart) || !finite(legEnd)) break;
            boolean overlaps = overlap.overlaps(
                    previous, current, legStart, legEnd, padding);
            if (overlaps) {
                furthestOverlappedLeg = legIndex;
            } else if (furthestOverlappedLeg >= 0) {
                break;
            }
            legStart = legEnd;
        }
        if (furthestOverlappedLeg > index) {
            index = furthestOverlappedLeg;
        }
        while (index < route.size()) {
            Vec3 waypoint = route.get(index);
            if (!finite(waypoint)
                    || !overlap.overlaps(previous, current, waypoint, waypoint, padding)) {
                break;
            }
            index++;
        }
        return new WaypointAdvance(index, index - first);
    }

    // Let integrations provide exact checkpoint overlap without exposing their hull implementation.
    @FunctionalInterface
    public interface WaypointOverlap {
        // Check whether one checkpoint was crossed by the live hull between two samples.
        boolean overlaps(Vec3 previousPosition, Vec3 currentPosition, Vec3 waypoint,
                         double capturePadding);
    }

    // Let integrations test route legs against their exact moving hull representation.
    @FunctionalInterface
    public interface RouteLegOverlap {
        // Check whether one retained leg was crossed by the live hull between two samples.
        boolean overlaps(Vec3 previousPosition, Vec3 currentPosition,
                         Vec3 legStart, Vec3 legEnd, double capturePadding);
    }

    // Locate the route checkpoint immediately after the retained leg nearest a live position.
    public static RouteCursor routeCursor(
            List<Waypoint> waypoints,
            int nextWaypointIndex,
            Vec3 routeOrigin,
            Vec3 currentPosition
    ) {
        RouteProjection projection = routeProjection(
                waypoints, nextWaypointIndex, routeOrigin, currentPosition);
        return projection.found() ? new RouteCursor(
                projection.nextWaypointIndex(), projection.distanceToRouteSqr())
                : RouteCursor.none();
    }

    // Project a live position onto the nearest remaining route leg.
    public static RouteProjection routeProjection(
            List<Waypoint> waypoints,
            int nextWaypointIndex,
            Vec3 routeOrigin,
            Vec3 currentPosition
    ) {
        List<Waypoint> route = waypoints == null ? List.of() : waypoints;
        if (route.isEmpty()) return RouteProjection.none();
        int first = Math.max(0, Math.min(nextWaypointIndex, route.size() - 1));
        Vec3 position = finite(currentPosition) ? currentPosition : Vec3.ZERO;
        RouteProjection selected = RouteProjection.none();
        for (int index = first; index < route.size(); index++) {
            Waypoint waypoint = route.get(index);
            if (waypoint == null) continue;
            RouteProjection candidate = routeLegProjection(
                    index, routeLegStart(route, index, routeOrigin, position),
                    waypoint.position(), position, waypoint.mode());
            if (!selected.found()
                    || candidate.distanceToRouteSqr()
                    < selected.distanceToRouteSqr() - EPSILON
                    || Math.abs(candidate.distanceToRouteSqr()
                    - selected.distanceToRouteSqr()) <= EPSILON
                    && candidate.nextWaypointIndex() > selected.nextWaypointIndex()) {
                selected = candidate;
            }
        }
        return selected;
    }

    // Project a live position onto one finite route leg.
    public static RouteProjection routeLegProjection(
            int nextWaypointIndex,
            Vec3 legStart,
            Vec3 legEnd,
            Vec3 currentPosition
    ) {
        return routeLegProjection(nextWaypointIndex, legStart, legEnd,
                currentPosition, RouteMode.FLIGHT);
    }

    /** Project onto one finite route leg using planar distance for ground routes. */
    public static RouteProjection routeLegProjection(
            int nextWaypointIndex,
            Vec3 legStart,
            Vec3 legEnd,
            Vec3 currentPosition,
            RouteMode mode
    ) {
        Vec3 position = finite(currentPosition) ? currentPosition : Vec3.ZERO;
        Vec3 start = finite(legStart) ? legStart : position;
        Vec3 end = finite(legEnd) ? legEnd : start;
        Vec3 delta = end.subtract(start);
        boolean ground = mode == RouteMode.GROUND;
        Vec3 measuredDelta = ground ? new Vec3(delta.x, 0.0D, delta.z) : delta;
        Vec3 measuredOffset = position.subtract(start);
        if (ground) {
            measuredOffset = new Vec3(measuredOffset.x, 0.0D, measuredOffset.z);
        }
        double lengthSqr = measuredDelta.lengthSqr();
        double progress = lengthSqr <= EPSILON ? 1.0D
                : measuredOffset.dot(measuredDelta) / lengthSqr;
        double clamped = Math.max(0.0D, Math.min(1.0D, progress));
        Vec3 projection = start.add(delta.scale(clamped));
        Vec3 distance = position.subtract(projection);
        if (ground) {
            distance = new Vec3(distance.x, 0.0D, distance.z);
        }
        return new RouteProjection(nextWaypointIndex, projection, progress,
                distance.lengthSqr());
    }

    // Orient reusable safe-route geometry toward either matching endpoint.
    public static OrientedRoute orientRoute(
            List<Waypoint> waypoints,
            Vec3 routeOrigin,
            Vec3 currentPosition,
            Vec3 destination,
            double endpointTolerance
    ) {
        List<Waypoint> route = waypoints == null ? List.of() : waypoints.stream()
                .filter(Objects::nonNull).toList();
        if (route.isEmpty() || !finite(destination)) return OrientedRoute.none();
        Vec3 origin = finite(routeOrigin) ? routeOrigin : route.getFirst().position();
        Vec3 target = route.getLast().position();
        double toleranceSqr = Math.pow(nonNegative(endpointTolerance, 0.0D), 2.0D);
        boolean forwardMatches = target.distanceToSqr(destination) <= toleranceSqr;
        boolean reverseMatches = origin.distanceToSqr(destination) <= toleranceSqr;
        if (!forwardMatches && !reverseMatches) return OrientedRoute.none();

        OrientedRoute forward = new OrientedRoute(origin, target, route, false);
        List<Waypoint> reversedWaypoints = reversedRouteWaypoints(origin, route);
        OrientedRoute reverse = new OrientedRoute(
                target, origin, reversedWaypoints, true);
        if (!forwardMatches) return reverse;
        if (!reverseMatches) return forward;
        Vec3 current = finite(currentPosition) ? currentPosition : origin;
        return routeTravelScore(reverse, current) + EPSILON
                < routeTravelScore(forward, current) ? reverse : forward;
    }

    // Route from the nearest safe graph leg to one caller-owned destination.
    public static OrientedRoute routeGraphPath(
            List<RouteLeg> routeLegs,
            Vec3 currentPosition,
            Vec3 destination,
            double endpointTolerance
    ) {
        return routeGraphPath(routeLegs, currentPosition, destination,
                endpointTolerance, endpointTolerance);
    }

    /**
     * Route from the nearest safe graph leg using independent graph-connection and live-destination
     * tolerances. Graph topology normally needs an exact tolerance, while a moving/capture-area
     * destination may legitimately match its retained terminal within a wider host-owned radius.
     */
    public static OrientedRoute routeGraphPath(
            List<RouteLeg> routeLegs,
            Vec3 currentPosition,
            Vec3 destination,
            double connectionTolerance,
            double destinationTolerance
    ) {
        List<RouteLeg> legs = routeLegs == null ? List.of() : routeLegs;
        if (legs.isEmpty() || !finite(destination)) return OrientedRoute.none();
        Vec3 current = finite(currentPosition) ? currentPosition : Vec3.ZERO;
        double connectionToleranceSqr = Math.pow(
                nonNegative(connectionTolerance, 0.0D), 2.0D);
        double destinationToleranceSqr = Math.pow(
                nonNegative(destinationTolerance, 0.0D), 2.0D);
        if (current.distanceToSqr(destination) <= destinationToleranceSqr) {
            return OrientedRoute.none();
        }
        RouteGraphCandidate selected = RouteGraphCandidate.none();
        for (int index = 0; index < legs.size(); index++) {
            RouteLeg leg = legs.get(index);
            RoutePolylineProjection projection = routeLegPolylineProjection(leg, current);
            if (!projection.found()) continue;
            boolean[] used = new boolean[legs.size()];
            used[index] = true;
            for (boolean reverse : List.of(false, true)) {
                Vec3 endpoint = reverse ? leg.origin() : leg.target();
                GraphContinuation continuation = shortestGraphContinuation(
                        legs, endpoint, destination, connectionToleranceSqr,
                        destinationToleranceSqr, used);
                if (!continuation.found()) continue;
                List<Waypoint> route = new ArrayList<>(routeLegRemainder(
                        leg, projection, reverse));
                appendRouteWaypoints(route, continuation.waypoints());
                if (route.isEmpty()) continue;
                double along = reverse ? projection.distanceFromOrigin()
                        : projection.totalLength() - projection.distanceFromOrigin();
                RouteGraphCandidate candidate = new RouteGraphCandidate(
                        projection.position(), route.getLast().position(), route,
                        reverse, projection.distanceToRouteSqr(),
                        along + continuation.distance());
                if (candidate.betterThan(selected)) selected = candidate;
            }
        }
        return selected.found() ? new OrientedRoute(
                selected.origin(), selected.target(), selected.waypoints(), selected.reversed())
                : OrientedRoute.none();
    }

    // Stitch a directed route graph into the complete connected path ending at one terminal leg.
    public static StitchedRoute stitchRouteGraph(
            List<RouteLeg> routeLegs,
            int terminalLegIndex,
            double endpointTolerance
    ) {
        List<RouteLeg> legs = routeLegs == null ? List.of() : routeLegs;
        if (terminalLegIndex < 0 || terminalLegIndex >= legs.size()
                || legs.get(terminalLegIndex) == null) return StitchedRoute.none();
        double toleranceSqr = Math.pow(nonNegative(endpointTolerance, 0.0D), 2.0D);
        RouteLeg terminal = legs.get(terminalLegIndex);
        boolean[] used = new boolean[legs.size()];
        used[terminalLegIndex] = true;
        List<Integer> cycle = new ArrayList<>();
        int[] remainingBranches = {routeGraphBranchBudget(legs.size())};
        if (findForwardRoute(legs, terminalLegIndex, terminal.target(), terminal.origin(),
                toleranceSqr, used, cycle, remainingBranches)) {
            cycle.add(terminalLegIndex);
            return stitchedRoute(legs, cycle, true);
        }

        used = new boolean[legs.size()];
        used[terminalLegIndex] = true;
        remainingBranches[0] = routeGraphBranchBudget(legs.size());
        List<Integer> route = longestPredecessorRoute(
                legs, terminalLegIndex, terminal.origin(), toleranceSqr,
                used, remainingBranches);
        route.add(terminalLegIndex);
        return stitchedRoute(legs, route, false);
    }

    // Create a reusable terminal region near an anchor without coupling routing to its owner.
    public static RouteTerminal routeTerminal(
            Vec3 anchor,
            Vec3 outward,
            double standOffDistance,
            double verticalOffset,
            double captureRadius
    ) {
        Vec3 center = finite(anchor) ? anchor : Vec3.ZERO;
        Vec3 direction = finite(outward) && outward.lengthSqr() > EPSILON
                ? outward.normalize() : new Vec3(0.0D, 0.0D, 1.0D);
        Vec3 position = center.add(direction.scale(
                nonNegative(standOffDistance, 0.0D))).add(
                0.0D, Double.isFinite(verticalOffset) ? verticalOffset : 0.0D, 0.0D);
        return new RouteTerminal(position, captureRadius);
    }

    // Find the first remaining live-clear route leg that can accept a local rejoin.
    public static RouteRejoin findRouteRejoin(
            @Nullable Level rootLevel,
            List<Waypoint> waypoints,
            int nextWaypointIndex,
            Vec3 currentPosition,
            Safety safety,
            Validator validator
    ) {
        List<Waypoint> route = waypoints == null ? List.of() : waypoints;
        if (route.isEmpty() || validator == null) return RouteRejoin.none();
        int first = Math.max(0, Math.min(nextWaypointIndex, route.size() - 1));
        Vec3 current = finite(currentPosition) ? currentPosition : Vec3.ZERO;
        Safety envelope = safety == null ? Safety.DEFAULT : safety;
        RouteRejoin detour = RouteRejoin.none();
        for (int index = first; index < route.size(); index++) {
            Waypoint entry = route.get(index);
            if (entry == null) continue;
            if (index + 1 < route.size()) {
                Waypoint exit = route.get(index + 1);
                if (exit == null) continue;
                Traversal onward = validator.validate(new Query(rootLevel, entry.position(),
                        exit.position(), envelope, exit.mode()));
                if (onward == null || onward.result() != TraversalResult.CLEAR) continue;
            }
            Traversal direct = validator.validate(new Query(rootLevel, current,
                    entry.position(), envelope, entry.mode()));
            if (direct != null && direct.result() == TraversalResult.CLEAR) {
                return new RouteRejoin(index, true);
            }
            if (!detour.found() && direct != null && direct.result() == TraversalResult.BLOCKED) {
                detour = new RouteRejoin(index, false);
            }
        }
        return detour;
    }

    // Scan a bounded suffix of a retained route for its next live-clear rejoin leg.
    // Callers resume from nextWaypointIndex() when this pass is incomplete, keeping
    // dynamic route repair proportional to a fixed per-tick validation budget.
    public static RouteRejoinScan scanRouteRejoin(
            @Nullable Level rootLevel,
            List<Waypoint> waypoints,
            int nextWaypointIndex,
            Vec3 currentPosition,
            Safety safety,
            Validator validator,
            int maximumWaypointChecks
    ) {
        List<Waypoint> route = waypoints == null ? List.of() : waypoints;
        if (route.isEmpty() || validator == null) return RouteRejoinScan.empty();
        int first = Math.max(0, Math.min(nextWaypointIndex, route.size() - 1));
        int maximum = Math.max(1, maximumWaypointChecks);
        int limit = Math.min(route.size(), first + maximum);
        Vec3 current = finite(currentPosition) ? currentPosition : Vec3.ZERO;
        Safety envelope = safety == null ? Safety.DEFAULT : safety;
        RouteRejoin detour = RouteRejoin.none();
        for (int index = first; index < limit; index++) {
            Waypoint entry = route.get(index);
            if (entry == null) continue;
            if (index + 1 < route.size()) {
                Waypoint exit = route.get(index + 1);
                if (exit == null) continue;
                Traversal onward = validator.validate(new Query(rootLevel, entry.position(),
                        exit.position(), envelope, exit.mode()));
                if (onward == null || onward.result() != TraversalResult.CLEAR) continue;
            }
            Traversal direct = validator.validate(new Query(rootLevel, current,
                    entry.position(), envelope, entry.mode()));
            if (direct != null && direct.result() == TraversalResult.CLEAR) {
                return new RouteRejoinScan(new RouteRejoin(index, true), index + 1,
                        index + 1 >= route.size());
            }
            if (!detour.found() && direct != null && direct.result() == TraversalResult.BLOCKED) {
                detour = new RouteRejoin(index, false);
            }
        }
        if (detour.found()) {
            return new RouteRejoinScan(detour, limit, limit >= route.size());
        }
        return new RouteRejoinScan(RouteRejoin.none(), limit, limit >= route.size());
    }

    // Find the nearest remaining route leg which can accept a live rejoin.
    public static RouteLegRejoin findRouteLegRejoin(
            @Nullable Level rootLevel,
            List<Waypoint> waypoints,
            int nextWaypointIndex,
            Vec3 routeOrigin,
            Vec3 currentPosition,
            Safety safety,
            Validator validator
    ) {
        int maximum = waypoints == null ? 1 : Math.max(1, waypoints.size());
        return scanRouteLegRejoin(rootLevel, waypoints, nextWaypointIndex,
                routeOrigin, currentPosition, safety, validator, maximum).rejoin();
    }

    // Scan forward from the nearest remaining route leg using a bounded validation budget.
    public static RouteLegRejoinScan scanRouteLegRejoin(
            @Nullable Level rootLevel,
            List<Waypoint> waypoints,
            int nextWaypointIndex,
            Vec3 routeOrigin,
            Vec3 currentPosition,
            Safety safety,
            Validator validator,
            int maximumLegChecks
    ) {
        List<Waypoint> route = waypoints == null ? List.of() : waypoints;
        if (route.isEmpty() || validator == null) return RouteLegRejoinScan.empty();
        int requestedFirst = Math.max(0, Math.min(nextWaypointIndex, route.size() - 1));
        Vec3 current = finite(currentPosition) ? currentPosition : Vec3.ZERO;
        RouteProjection nearest = routeProjection(
                route, requestedFirst, routeOrigin, current);
        if (!nearest.found()) return RouteLegRejoinScan.empty();
        int first = nearest.nextWaypointIndex();
        int maximum = Math.max(1, maximumLegChecks);
        int limit = Math.min(route.size(), first + maximum);
        Safety envelope = safety == null ? Safety.DEFAULT : safety;
        for (int index = first; index < limit; index++) {
            Waypoint endpoint = route.get(index);
            if (endpoint == null) continue;
            RouteProjection projection = routeLegProjection(
                    index, routeLegStart(route, index, routeOrigin, current),
                    endpoint.position(), current, endpoint.mode());
            Vec3 onwardTarget = endpoint.position();
            RouteMode onwardMode = endpoint.mode();
            if (projection.position().distanceToSqr(onwardTarget) <= EPSILON
                    && index + 1 < route.size() && route.get(index + 1) != null) {
                onwardTarget = route.get(index + 1).position();
                onwardMode = route.get(index + 1).mode();
            }
            Traversal onward = validator.validate(new Query(
                    rootLevel, projection.position(), onwardTarget,
                    envelope, onwardMode));
            if (onward != null && onward.result() == TraversalResult.CLEAR) {
                Traversal direct = validator.validate(new Query(
                        rootLevel, current, projection.position(),
                        envelope, endpoint.mode()));
                if (direct != null && direct.result() == TraversalResult.CLEAR) {
                    return new RouteLegRejoinScan(new RouteLegRejoin(
                            index, projection.position(), true), index + 1,
                            index + 1 >= route.size());
                }
                if (direct != null && direct.result() == TraversalResult.BLOCKED) {
                    return new RouteLegRejoinScan(new RouteLegRejoin(
                            index, projection.position(), false), index + 1,
                            index + 1 >= route.size());
                }
            }
            // A new obstacle may split a long retained leg after the nearest
            // projection. Keep the leg endpoint as a local-detour rejoin when
            // its following suffix is still live-clear. This also gives a
            // blocked final leg a target beyond the obstruction instead of an
            // empty scan which forces its consumer to stop forever.
            if (onward != null && onward.result() == TraversalResult.BLOCKED
                    && projection.position().distanceToSqr(endpoint.position()) > EPSILON) {
                Waypoint suffixEndpoint = null;
                for (int suffixIndex = index + 1;
                     suffixIndex < route.size() && suffixEndpoint == null; suffixIndex++) {
                    suffixEndpoint = route.get(suffixIndex);
                }
                Traversal suffix = suffixEndpoint == null
                        ? Traversal.clear(endpoint.mode())
                        : validator.validate(new Query(
                        rootLevel, endpoint.position(), suffixEndpoint.position(),
                        envelope, suffixEndpoint.mode()));
                if (suffix != null && suffix.result() == TraversalResult.CLEAR) {
                    Traversal direct = validator.validate(new Query(
                            rootLevel, current, endpoint.position(),
                            envelope, endpoint.mode()));
                    if (direct != null && direct.result() != TraversalResult.UNAVAILABLE) {
                        return new RouteLegRejoinScan(new RouteLegRejoin(
                                index, endpoint.position(),
                                direct.result() == TraversalResult.CLEAR), index + 1,
                                index + 1 >= route.size());
                    }
                }
            }
        }
        return new RouteLegRejoinScan(
                RouteLegRejoin.none(), limit, limit >= route.size());
    }

    // Create a Sable collision validator which never loads an absent root or plot chunk
    public static Validator sableCollisionValidator(CollisionOptions options) {
        return sableCollisionValidator(options, GroundContactPolicy.NONE, CollisionPrecision.PROBED);
    }

    // Create a Sable collision validator with an optional supported-ground contact policy.
    public static Validator sableCollisionValidator(
            CollisionOptions options,
            GroundContactPolicy groundContactPolicy
    ) {
        return sableCollisionValidator(options, groundContactPolicy, CollisionPrecision.PROBED);
    }

    // Create a Sable collision validator with an explicit collision precision policy.
    public static Validator sableCollisionValidator(
            CollisionOptions options,
            GroundContactPolicy groundContactPolicy,
            CollisionPrecision precision
    ) {
        CollisionOptions resolved = options == null ? CollisionOptions.DEFAULT : options;
        GroundContactPolicy resolvedGroundContact = groundContactPolicy == null
                ? GroundContactPolicy.NONE : groundContactPolicy;
        CollisionPrecision resolvedPrecision = precision == null
                ? CollisionPrecision.PROBED : precision;
        return query -> validateSableCollision(
                query, resolved, resolvedGroundContact, resolvedPrecision);
    }

    // Combine two policies, preserving unavailable state before accepting a route segment
    public static Validator allOf(Validator first, Validator second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        return query -> {
            Traversal initial = first.validate(query);
            if (initial == null || initial.result() != TraversalResult.CLEAR) {
                return initial == null ? Traversal.blocked() : initial;
            }
            Traversal next = second.validate(query);
            return next == null ? Traversal.blocked() : next;
        };
    }

    // Validate a segment through live Sable geometry and loaded chunks only
    private static Traversal validateSableCollision(
            Query query,
            CollisionOptions options,
            GroundContactPolicy groundContactPolicy,
            CollisionPrecision precision
    ) {
        if (query == null || query.rootLevel() == null || !finite(query.start()) || !finite(query.end())) {
            return Traversal.unavailable();
        }
        try {
            AABB startBounds = collisionBounds(query, query.start(), groundContactPolicy);
            AABB endBounds = collisionBounds(query, query.end(), groundContactPolicy);
            AABB sweptBounds = startBounds.minmax(endBounds);
            if (!loaded(query.rootLevel(), query.start(), query.end(), query.safety(),
                    sweptBounds, options.excludedSubLevelIds())) {
                return Traversal.unavailable();
            }

            Vec3 delta = query.end().subtract(query.start());
            double distance = delta.length();
            if (distance <= EPSILON) {
                return Traversal.clear(query.mode());
            }
            double clearDistance = precision == CollisionPrecision.SWEPT
                    ? SubLevelParticleOcclusion.findSweptBoundsBlockingDistance(
                    query.rootLevel(), null, query.start(), delta, distance,
                    List.of(startBounds), options.includeRootLevel(),
                    options.excludedSubLevelIds(), options.includeTaggedTransparentBlocks())
                    : SubLevelParticleOcclusion.findProbedBoundsBlockingDistance(
                    query.rootLevel(), null, delta, distance,
                    List.of(startBounds), options.includeRootLevel(), options.excludedSubLevelIds(),
                    options.includeTaggedTransparentBlocks(), options.maximumProbesPerBounds());
            return clearDistance + EPSILON >= distance
                    ? Traversal.clear(query.mode()) : Traversal.blocked();
        } catch (RuntimeException | LinkageError ignored) {
            // Sable can replace a body between path samples; retry after the live state settles.
            return Traversal.unavailable();
        }
    }

    // Build the collision envelope for one segment endpoint.
    private static AABB collisionBounds(
            Query query,
            Vec3 center,
            GroundContactPolicy groundContactPolicy
    ) {
        AABB bounds = query.safety().boundsAt(center);
        return query.mode() == RouteMode.GROUND
                ? groundContactPolicy.removeSupportContact(bounds) : bounds;
    }

    // Check every occupied root and Sable plot chunk without creating a chunk ticket
    private static boolean loaded(Level rootLevel, Vec3 start, Vec3 end, Safety safety,
                                  AABB sweptBounds, Set<UUID> excludedSubLevelIds) {
        int samples = Math.max(1, (int) Math.ceil(start.distanceTo(end)
                / Math.max(0.5D, Math.min(4.0D, safety.horizontalRadius()))));
        for (int index = 0; index <= samples; index++) {
            Vec3 point = start.lerp(end, index / (double) samples);
            if (!rootLoaded(rootLevel, safety.boundsAt(point))) {
                return false;
            }
        }

        for (SubLevel subLevel : SableTransformApi.intersecting(rootLevel, sweptBounds)) {
            if (subLevel == null || subLevel.isRemoved()
                    || excludedSubLevelIds.contains(subLevel.getUniqueId())) {
                continue;
            }
            for (int index = 0; index <= samples; index++) {
                Vec3 worldPoint = start.lerp(end, index / (double) samples);
                if (!subLevelLoaded(rootLevel, subLevel, safety.boundsAt(worldPoint))) {
                    return false;
                }
            }
        }
        return true;
    }

    // Check the root chunks occupied by one conservative envelope
    private static boolean rootLoaded(Level rootLevel, AABB bounds) {
        BlockPos sample = BlockPos.containing(bounds.getCenter());
        for (int chunkX = ((int) Math.floor(bounds.minX)) >> 4;
             chunkX <= ((int) Math.floor(bounds.maxX)) >> 4; chunkX++) {
            for (int chunkZ = ((int) Math.floor(bounds.minZ)) >> 4;
                 chunkZ <= ((int) Math.floor(bounds.maxZ)) >> 4; chunkZ++) {
                BlockPos chunkOrigin = new BlockPos(chunkX << 4, sample.getY(), chunkZ << 4);
                if (!SubLevelBlockEntityCollector.isTargetLoaded(rootLevel, null, chunkOrigin)) {
                    return false;
                }
            }
        }
        return true;
    }

    // Check the relevant local plot chunks occupied by one conservative envelope
    private static boolean subLevelLoaded(Level rootLevel, SubLevel subLevel, AABB worldBounds) {
        if (subLevel.getPlot() == null) {
            return false;
        }
        AABB localBounds = localBounds(subLevel, worldBounds);
        BlockPos sample = BlockPos.containing(localBounds.getCenter());
        for (int chunkX = ((int) Math.floor(localBounds.minX)) >> 4;
             chunkX <= ((int) Math.floor(localBounds.maxX)) >> 4; chunkX++) {
            for (int chunkZ = ((int) Math.floor(localBounds.minZ)) >> 4;
                 chunkZ <= ((int) Math.floor(localBounds.maxZ)) >> 4; chunkZ++) {
                ChunkPos localChunk = new ChunkPos(chunkX, chunkZ);
                if (!subLevel.getPlot().contains(localChunk)) {
                    continue;
                }
                BlockPos chunkOrigin = new BlockPos(chunkX << 4, sample.getY(), chunkZ << 4);
                if (!SubLevelBlockEntityCollector.isTargetLoaded(rootLevel,
                        subLevel.getUniqueId(), chunkOrigin)) {
                    return false;
                }
            }
        }
        return true;
    }

    // Convert one world envelope into a conservative local sublevel envelope
    private static AABB localBounds(SubLevel subLevel, AABB worldBounds) {
        AABB result = null;
        for (double x : List.of(worldBounds.minX, worldBounds.maxX)) {
            for (double y : List.of(worldBounds.minY, worldBounds.maxY)) {
                for (double z : List.of(worldBounds.minZ, worldBounds.maxZ)) {
                    Vec3 local = SableTransformApi.toLocalPosition(subLevel, new Vec3(x, y, z));
                    AABB point = new AABB(local, local);
                    result = result == null ? point : result.minmax(point);
                }
            }
        }
        return result == null ? new AABB(Vec3.ZERO, Vec3.ZERO) : result;
    }

    // Test the direct final segment using every enabled movement mode
    private static Traversal traverse(Request request, Vec3 start, Vec3 target) {
        Traversal unavailable = null;
        for (RouteMode mode : request.movement().modes()) {
            Traversal traversal = request.validator().validate(new Query(
                    request.rootLevel(), start, target, request.safety(), mode));
            if (traversal != null && traversal.result() == TraversalResult.CLEAR) {
                return traversal;
            }
            if (traversal != null && traversal.result() == TraversalResult.UNAVAILABLE) {
                unavailable = traversal;
            }
        }
        return unavailable == null ? Traversal.blocked() : unavailable;
    }

    // Build the available next grid moves
    private static List<Candidate> candidates(Request request) {
        List<Candidate> result = new ArrayList<>();
        for (RouteMode mode : request.movement().modes()) {
            List<GridStep> steps = mode.spatial() ? SPATIAL_STEPS : PLANAR_STEPS;
            for (GridStep step : steps) {
                if (!mode.spatial() && Math.abs(step.y()) > request.movement().maximumGroundRiseSteps()) {
                    continue;
                }
                result.add(new Candidate(step, mode));
            }
        }
        return result;
    }

    // Check one grid node against the caller-selected route search range
    private static boolean withinRange(Node node, Request request) {
        double spacing = request.nodeSpacing();
        return Math.sqrt(node.x() * node.x() + node.y() * node.y() + node.z() * node.z())
                * spacing <= request.maximumRange() + EPSILON;
    }

    // Build a complete, safe waypoint sequence.
    private static Result complete(Request request, State terminal, Vec3 target,
                                   @Nullable RouteMode mode, int expanded) {
        List<Waypoint> route = new ArrayList<>(compactWaypoints(buildRawPartial(terminal)));
        if (route.isEmpty() || route.getLast().position().distanceToSqr(target) > EPSILON) {
            route.add(new Waypoint(target, mode));
        }
        return new Result(Outcome.COMPLETE, simplifyCompletedRoute(request, route), expanded);
    }

    // Build the safe path to a previously validated grid node without changing its path geometry.
    private static List<Waypoint> buildRawPartial(State terminal) {
        ArrayDeque<Waypoint> route = new ArrayDeque<>();
        State cursor = terminal;
        while (cursor != null && cursor.parent() != null) {
            route.addFirst(new Waypoint(cursor.position(), cursor.mode()));
            cursor = cursor.previous();
        }
        return List.copyOf(route);
    }

    // Build a compact partial path for in-progress diagnostics only.
    private static List<Waypoint> buildPartial(State terminal) {
        return compactWaypoints(buildRawPartial(terminal));
    }

    // Greedily replace grid-aligned runs with an exactly revalidated direct leg. This retains every
    // required bend but removes harmless staircase turns and diagonal grid artefacts from completed routes.
    private static List<Waypoint> simplifyCompletedRoute(Request request, List<Waypoint> route) {
        if (route == null || route.size() < 2 || request == null) {
            return route == null ? List.of() : List.copyOf(route);
        }
        Vec3 anchor = request.start().resolve(request.rootLevel());
        if (!finite(anchor)) {
            return compactWaypoints(route);
        }
        List<Waypoint> simplified = new ArrayList<>();
        int candidate = 0;
        int validations = 0;
        // Route simplification must not erase the caller's initial forward commitment. Preserve
        // the accepted prefix through that commitment before shortcutting the remaining safe route.
        Vec3 routeStart = anchor;
        while (candidate < route.size()
                && request.preferences().minimumForwardCommitDistance() > EPSILON) {
            Waypoint committed = route.get(candidate);
            candidate++;
            if (committed != null) {
                simplified.add(committed);
                anchor = committed.position();
                if (request.preferences().forwardCommitted(request.preferences().forwardProgress(
                        committed.position().subtract(routeStart)))) {
                    break;
                }
            }
        }
        while (candidate < route.size()) {
            int accepted = farthestSafeShortcut(request, route, candidate, anchor,
                    MAX_COMPLETED_ROUTE_SHORTCUT_VALIDATIONS - validations);
            validations += Math.max(1, shortcutValidationCount(route, candidate, accepted));
            if (accepted < candidate) {
                // Every A* edge was accepted before this method is reached. Preserve that edge if a
                // stateful external validator cannot repeat its observation during cosmetic compaction.
                Waypoint retained = route.get(candidate);
                if (retained != null) {
                    simplified.add(retained);
                    anchor = retained.position();
                }
                candidate++;
                continue;
            }
            Waypoint retained = route.get(accepted);
            if (retained != null && (simplified.isEmpty()
                    || simplified.getLast().position().distanceToSqr(retained.position()) > EPSILON)) {
                simplified.add(retained);
                anchor = retained.position();
            }
            candidate = accepted + 1;
        }
        if (!route.isEmpty()) {
            Waypoint terminal = route.getLast();
            if (terminal != null && (simplified.isEmpty()
                    || simplified.getLast().position().distanceToSqr(terminal.position()) > EPSILON)) {
                simplified.add(terminal);
            }
        }
        return List.copyOf(simplified);
    }

    // Find the furthest point which can replace a grid-aligned run. Test the destination first,
    // then narrow toward the last known-clear point so a long straight leg does not consume one
    // validation per grid cell.
    private static int farthestSafeShortcut(
            Request request,
            List<Waypoint> route,
            int first,
            Vec3 anchor,
            int remainingValidations
    ) {
        if (remainingValidations <= 0 || first < 0 || first >= route.size()) {
            return -1;
        }
        int last = route.size() - 1;
        Waypoint terminal = route.get(last);
        if (terminal != null && shortcutClear(request, anchor, terminal.position())) {
            return last;
        }
        // The first raw edge was validated by A*. It is retained as the fallback, while binary
        // search finds the furthest exact direct shortcut before the next required bend.
        int low = first;
        int high = last - 1;
        int accepted = -1;
        int used = 1;
        while (low <= high && used < remainingValidations) {
            int middle = low + (high - low) / 2;
            Waypoint waypoint = route.get(middle);
            used++;
            if (waypoint != null && shortcutClear(request, anchor, waypoint.position())) {
                accepted = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return accepted;
    }

    // Approximate the bounded shortcut work used by the farthest-first search. This is deliberately
    // conservative so a pathological route cannot exceed the completed-route validation budget.
    private static int shortcutValidationCount(List<Waypoint> route, int first, int accepted) {
        if (route == null || route.isEmpty() || first < 0 || first >= route.size()) {
            return 1;
        }
        int span = Math.max(1, route.size() - first);
        int validations = 1;
        while (span > 1) {
            validations++;
            span = (span + 1) / 2;
        }
        return validations;
    }

    // Validate an emitted shortcut through the caller's exact swept-hull policy.
    private static boolean shortcutClear(Request request, Vec3 anchor, Vec3 target) {
        Traversal traversal = traverse(request, anchor, target);
        return traversal != null && traversal.result() == TraversalResult.CLEAR;
    }

    // Keep every turn while collapsing a straight run of grid samples into one safe segment.
    private static List<Waypoint> compactWaypoints(List<Waypoint> route) {
        if (route == null || route.size() < 3) return route == null ? List.of() : List.copyOf(route);
        List<Waypoint> compact = new ArrayList<>();
        compact.add(route.getFirst());
        for (int index = 1; index < route.size() - 1; index++) {
            Waypoint previous = route.get(index - 1);
            Waypoint current = route.get(index);
            Waypoint next = route.get(index + 1);
            Vec3 before = current.position().subtract(previous.position());
            Vec3 after = next.position().subtract(current.position());
            if (current.mode() == next.mode() && previous.mode() == current.mode()
                    && before.lengthSqr() > EPSILON && after.lengthSqr() > EPSILON
                    && before.cross(after).lengthSqr() <= EPSILON
                    && before.dot(after) > EPSILON) {
                continue;
            }
            compact.add(current);
        }
        compact.add(route.getLast());
        return List.copyOf(compact);
    }

    // Get the caller-weighted grid A* distance estimate
    private static double heuristic(Request request, Vec3 from, Vec3 target) {
        return request.preferences().heuristicCost(from, target);
    }

    // Get every spatial step once
    private static List<GridStep> spatialSteps() {
        List<GridStep> steps = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x != 0 || y != 0 || z != 0) {
                        steps.add(new GridStep(x, y, z));
                    }
                }
            }
        }
        return List.copyOf(steps);
    }

    // Get every planar step with a bounded one-cell rise or descent
    private static List<GridStep> planarSteps() {
        List<GridStep> steps = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) {
                    continue;
                }
                for (int y = -1; y <= 1; y++) {
                    steps.add(new GridStep(x, y, z));
                }
            }
        }
        return List.copyOf(steps);
    }

    // Check whether a vector is finite
    private static boolean finite(@Nullable Vec3 value) {
        return value != null && Double.isFinite(value.x)
                && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    // Resolve the start of one retained route leg.
    private static Vec3 routeLegStart(
            List<Waypoint> route,
            int waypointIndex,
            Vec3 routeOrigin,
            Vec3 fallback
    ) {
        for (int index = Math.min(waypointIndex - 1, route.size() - 1);
             index >= 0; index--) {
            Waypoint waypoint = route.get(index);
            if (waypoint != null) return waypoint.position();
        }
        return finite(routeOrigin) ? routeOrigin
                : finite(fallback) ? fallback : Vec3.ZERO;
    }

    // Reverse route segments while retaining the movement mode which validated each segment.
    private static List<Waypoint> reversedRouteWaypoints(
            Vec3 routeOrigin,
            List<Waypoint> route
    ) {
        List<Waypoint> reversed = new ArrayList<>();
        for (int index = route.size() - 1; index >= 0; index--) {
            Vec3 position = index == 0
                    ? routeOrigin : route.get(index - 1).position();
            reversed.add(new Waypoint(position, route.get(index).mode()));
        }
        return List.copyOf(reversed);
    }

    // Project one position onto every segment of an independently validated route leg.
    private static RoutePolylineProjection routeLegPolylineProjection(
            @Nullable RouteLeg leg,
            Vec3 currentPosition
    ) {
        if (leg == null || leg.waypoints().isEmpty()) {
            return RoutePolylineProjection.none();
        }
        Vec3 current = finite(currentPosition) ? currentPosition : Vec3.ZERO;
        Vec3 start = leg.origin();
        double traversed = 0.0D;
        int selectedSegment = -1;
        Vec3 selectedPosition = Vec3.ZERO;
        double selectedDistance = Double.POSITIVE_INFINITY;
        double selectedTraversed = 0.0D;
        for (int index = 0; index < leg.waypoints().size(); index++) {
            Vec3 end = leg.waypoints().get(index).position();
            double segmentLength = start.distanceTo(end);
            RouteProjection projection = routeLegProjection(
                    index, start, end, current, leg.waypoints().get(index).mode());
            double segmentProgress = Math.max(0.0D, Math.min(1.0D, projection.legProgress()));
            if (projection.distanceToRouteSqr() < selectedDistance - EPSILON
                    || Math.abs(projection.distanceToRouteSqr() - selectedDistance) <= EPSILON
                    && index > selectedSegment) {
                selectedSegment = index;
                selectedPosition = projection.position();
                selectedDistance = projection.distanceToRouteSqr();
                selectedTraversed = traversed + segmentLength * segmentProgress;
            }
            traversed += segmentLength;
            start = end;
        }
        return selectedSegment < 0 ? RoutePolylineProjection.none()
                : new RoutePolylineProjection(selectedSegment, selectedPosition,
                selectedDistance, selectedTraversed, traversed);
    }

    // Keep the part of one graph leg between a projected join and the selected endpoint.
    private static List<Waypoint> routeLegRemainder(
            RouteLeg leg,
            RoutePolylineProjection projection,
            boolean reverse
    ) {
        List<Waypoint> route = new ArrayList<>();
        Vec3 previous = projection.position();
        if (!reverse) {
            for (int index = projection.segmentIndex(); index < leg.waypoints().size(); index++) {
                Waypoint waypoint = leg.waypoints().get(index);
                appendRouteWaypoint(route, previous, waypoint);
                previous = waypoint.position();
            }
            return List.copyOf(route);
        }
        for (int index = projection.segmentIndex(); index >= 0; index--) {
            Vec3 position = index == 0 ? leg.origin()
                    : leg.waypoints().get(index - 1).position();
            Waypoint waypoint = new Waypoint(position, leg.waypoints().get(index).mode());
            appendRouteWaypoint(route, previous, waypoint);
            previous = waypoint.position();
        }
        return List.copyOf(route);
    }

    // Find the shortest safe graph continuation with deterministic Dijkstra traversal. The former
    // recursive branch walk could exhaust its search cap on dense schedule graphs before visiting
    // an otherwise connected destination, incorrectly reporting that no calculated route existed.
    private static GraphContinuation shortestGraphContinuation(
            List<RouteLeg> legs,
            Vec3 current,
            Vec3 destination,
            double connectionToleranceSqr,
            double destinationToleranceSqr,
            boolean[] excluded
    ) {
        if (current.distanceToSqr(destination) <= destinationToleranceSqr) {
            return GraphContinuation.complete();
        }
        int endpointCount = legs.size() * 2;
        double[] distances = new double[endpointCount];
        java.util.Arrays.fill(distances, Double.POSITIVE_INFINITY);
        PriorityQueue<GraphSearchNode> frontier = new PriorityQueue<>(
                Comparator.comparingDouble(GraphSearchNode::distance)
                        .thenComparingInt(GraphSearchNode::endpointIndex));

        // The selected route-leg endpoint can coincide with several retained graph nodes. Seed all
        // of them so tolerance-based graph connections remain topology, not extra travel geometry.
        for (int index = 0; index < legs.size(); index++) {
            RouteLeg leg = legs.get(index);
            if (leg == null || leg.waypoints().isEmpty()) continue;
            int originEndpoint = index * 2;
            int targetEndpoint = originEndpoint + 1;
            if (leg.origin().distanceToSqr(current) <= connectionToleranceSqr) {
                distances[originEndpoint] = 0.0D;
                frontier.add(new GraphSearchNode(originEndpoint, 0.0D, List.of()));
            }
            if (leg.target().distanceToSqr(current) <= connectionToleranceSqr) {
                distances[targetEndpoint] = 0.0D;
                frontier.add(new GraphSearchNode(targetEndpoint, 0.0D, List.of()));
            }
        }
        while (!frontier.isEmpty()) {
            GraphSearchNode node = frontier.poll();
            if (node.distance() > distances[node.endpointIndex()] + EPSILON) continue;
            Vec3 endpoint = graphEndpoint(legs, node.endpointIndex());
            if (endpoint.distanceToSqr(destination) <= destinationToleranceSqr) {
                return new GraphContinuation(true, node.waypoints(), node.distance());
            }
            for (int index = 0; index < legs.size(); index++) {
                RouteLeg leg = legs.get(index);
                if (index < excluded.length && excluded[index]
                        || leg == null || leg.waypoints().isEmpty()) continue;
                if (leg.origin().distanceToSqr(endpoint) <= connectionToleranceSqr) {
                    relaxGraphEndpoint(frontier, distances, node, index * 2 + 1,
                            leg.origin(), leg.waypoints());
                }
                if (leg.target().distanceToSqr(endpoint) <= connectionToleranceSqr) {
                    List<Waypoint> reverse = reversedRouteWaypoints(
                            leg.origin(), leg.waypoints());
                    relaxGraphEndpoint(frontier, distances, node, index * 2,
                            leg.target(), reverse);
                }
            }
        }
        return GraphContinuation.none();
    }

    // Relax one oriented retained graph leg while preserving all validated waypoint modes.
    private static void relaxGraphEndpoint(
            PriorityQueue<GraphSearchNode> frontier,
            double[] distances,
            GraphSearchNode current,
            int endpointIndex,
            Vec3 origin,
            List<Waypoint> appended
    ) {
        double distance = current.distance() + routeLength(origin, appended);
        if (distance + EPSILON >= distances[endpointIndex]) return;
        List<Waypoint> route = new ArrayList<>(current.waypoints());
        appendRouteWaypoints(route, appended);
        distances[endpointIndex] = distance;
        frontier.add(new GraphSearchNode(endpointIndex, distance, List.copyOf(route)));
    }

    // Resolve one endpoint index from the flattened origin/target node array.
    private static Vec3 graphEndpoint(List<RouteLeg> legs, int endpointIndex) {
        RouteLeg leg = legs.get(endpointIndex / 2);
        return endpointIndex % 2 == 0 ? leg.origin() : leg.target();
    }

    // Append route geometry without retaining duplicate graph endpoints.
    private static void appendRouteWaypoints(
            List<Waypoint> route,
            List<Waypoint> appended
    ) {
        if (appended == null) return;
        Vec3 origin = route.isEmpty() ? null : route.getLast().position();
        for (Waypoint waypoint : appended) {
            appendRouteWaypoint(route, origin, waypoint);
            if (waypoint != null) origin = waypoint.position();
        }
    }

    // Append one non-duplicate route waypoint.
    private static void appendRouteWaypoint(
            List<Waypoint> route,
            @Nullable Vec3 origin,
            @Nullable Waypoint waypoint
    ) {
        if (waypoint == null || origin != null
                && origin.distanceToSqr(waypoint.position()) <= EPSILON) return;
        route.add(waypoint);
    }

    // Measure a route from one explicit origin.
    private static double routeLength(Vec3 origin, List<Waypoint> route) {
        Vec3 previous = origin;
        double length = 0.0D;
        for (Waypoint waypoint : route) {
            if (waypoint == null) continue;
            length += previous.distanceTo(waypoint.position());
            previous = waypoint.position();
        }
        return length;
    }

    // Rank an oriented route by its nearest join plus the safe geometry remaining to its target.
    private static double routeTravelScore(OrientedRoute route, Vec3 currentPosition) {
        RouteProjection projection = routeProjection(
                route.waypoints(), 0, route.origin(), currentPosition);
        if (!projection.found()) return Double.POSITIVE_INFINITY;
        double distance = Math.sqrt(projection.distanceToRouteSqr())
                + projection.position().distanceTo(
                route.waypoints().get(projection.nextWaypointIndex()).position());
        for (int index = projection.nextWaypointIndex() + 1;
             index < route.waypoints().size(); index++) {
            distance += route.waypoints().get(index - 1).position()
                    .distanceTo(route.waypoints().get(index).position());
        }
        return distance;
    }

    // Find a simple forward path between two graph endpoints, backtracking past stale branches.
    private static boolean findForwardRoute(
            List<RouteLeg> legs,
            int terminalLegIndex,
            Vec3 current,
            Vec3 target,
            double toleranceSqr,
            boolean[] used,
            List<Integer> route,
            int[] remainingBranches
    ) {
        if (current.distanceToSqr(target) <= toleranceSqr) return true;
        if (remainingBranches[0]-- <= 0) return false;
        for (int index = 0; index < legs.size(); index++) {
            RouteLeg candidate = legs.get(index);
            if (index == terminalLegIndex || used[index] || candidate == null
                    || candidate.origin().distanceToSqr(current) > toleranceSqr) continue;
            used[index] = true;
            route.add(index);
            if (findForwardRoute(legs, terminalLegIndex, candidate.target(), target,
                    toleranceSqr, used, route, remainingBranches)) return true;
            route.removeLast();
            used[index] = false;
        }
        return false;
    }

    // Find the longest simple predecessor chain feeding a terminal graph endpoint.
    private static List<Integer> longestPredecessorRoute(
            List<RouteLeg> legs,
            int terminalLegIndex,
            Vec3 target,
            double toleranceSqr,
            boolean[] used,
            int[] remainingBranches
    ) {
        List<Integer> best = new ArrayList<>();
        if (remainingBranches[0]-- <= 0) return best;
        for (int index = 0; index < legs.size(); index++) {
            RouteLeg candidate = legs.get(index);
            if (index == terminalLegIndex || used[index] || candidate == null
                    || candidate.target().distanceToSqr(target) > toleranceSqr) continue;
            used[index] = true;
            List<Integer> route = longestPredecessorRoute(
                    legs, terminalLegIndex, candidate.origin(), toleranceSqr,
                    used, remainingBranches);
            route.add(index);
            used[index] = false;
            if (route.size() > best.size()) best = route;
        }
        return best;
    }

    // Bound branch backtracking for stale or heavily duplicated retained route graphs.
    private static int routeGraphBranchBudget(int legCount) {
        long scaled = Math.max(64L, (long) Math.max(0, legCount) * legCount * 4L);
        return (int) Math.min(MAX_ROUTE_GRAPH_BRANCH_SEARCH, scaled);
    }

    // Flatten a connected route-leg sequence while retaining its validated waypoint modes.
    private static StitchedRoute stitchedRoute(
            List<RouteLeg> legs,
            List<Integer> route,
            boolean closed
    ) {
        if (route.isEmpty()) return StitchedRoute.none();
        List<Waypoint> waypoints = new ArrayList<>();
        for (int index : route) {
            RouteLeg leg = legs.get(index);
            for (Waypoint waypoint : leg.waypoints()) {
                if (waypoint != null && (waypoints.isEmpty()
                        || waypoints.getLast().position().distanceToSqr(waypoint.position()) > EPSILON)) {
                    waypoints.add(waypoint);
                }
            }
        }
        RouteLeg first = legs.get(route.getFirst());
        RouteLeg last = legs.get(route.getLast());
        return waypoints.isEmpty() ? StitchedRoute.none()
                : new StitchedRoute(first.origin(), last.target(), waypoints, route.size(), closed);
    }

    // Check a finite segment against an axis-aligned hull.
    private static boolean segmentIntersects(AABB bounds, Vec3 start, Vec3 end) {
        if (bounds == null || !finite(start) || !finite(end)) return false;
        Vec3 delta = end.subtract(start);
        double[] range = {0.0D, 1.0D};
        return clipSegmentAxis(start.x, delta.x, bounds.minX, bounds.maxX, range)
                && clipSegmentAxis(start.y, delta.y, bounds.minY, bounds.maxY, range)
                && clipSegmentAxis(start.z, delta.z, bounds.minZ, bounds.maxZ, range);
    }

    // Clip a segment parameter range against one hull axis.
    private static boolean clipSegmentAxis(
            double origin,
            double delta,
            double minimum,
            double maximum,
            double[] range
    ) {
        if (Math.abs(delta) <= EPSILON) {
            return origin >= minimum && origin <= maximum;
        }
        double first = (minimum - origin) / delta;
        double second = (maximum - origin) / delta;
        if (first > second) {
            double swap = first;
            first = second;
            second = swap;
        }
        range[0] = Math.max(range[0], first);
        range[1] = Math.min(range[1], second);
        return range[0] <= range[1];
    }

    // Normalize one finite direction
    private static Vec3 normalize(@Nullable Vec3 value, @Nullable Vec3 fallback) {
        if (finite(value) && value.lengthSqr() > EPSILON) {
            return value.normalize();
        }
        return finite(fallback) && fallback.lengthSqr() > EPSILON
                ? fallback.normalize() : Vec3.ZERO;
    }

    // Normalize a positive value
    private static double positive(double value, double fallback) {
        return Double.isFinite(value) && value > EPSILON ? value : fallback;
    }

    // Normalize a non-negative value
    private static double nonNegative(double value, double fallback) {
        return Double.isFinite(value) ? Math.max(0.0D, value) : fallback;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            API
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Store a root-world or body-local endpoint which is resolved afresh for every plan
    public record Location(@Nullable UUID subLevelId, Vec3 localPosition) {
        // Initialize the route location
        public Location {
            localPosition = finite(localPosition) ? localPosition : Vec3.ZERO;
        }

        // Create a root-world endpoint
        public static Location world(Vec3 position) {
            return new Location(null, position);
        }

        // Create a body-local endpoint
        public static Location subLevel(UUID subLevelId, Vec3 position) {
            if (subLevelId == null) {
                throw new IllegalArgumentException("A sublevel location requires a sublevel id");
            }
            return new Location(subLevelId, position);
        }

        // Resolve this endpoint into the current root-world coordinate system
        public @Nullable Vec3 resolve(@Nullable Level rootLevel) {
            if (subLevelId == null) {
                return localPosition;
            }
            SubLevel subLevel = SableLevelApi.subLevel(rootLevel, subLevelId);
            if (subLevel == null) {
                return null;
            }
            Vec3 world = SableTransformApi.toWorldPosition(subLevel, localPosition);
            return SableTransformApi.projectOut(rootLevel, world);
        }
    }

    // Store one non-entity route planning request
    public record Request(
            @Nullable Level rootLevel,
            Location start,
            Location target,
            Safety safety,
            Movement movement,
            double nodeSpacing,
            double maximumRange,
            int maximumExpansions,
            double captureRadius,
            Validator validator,
            RoutePreferences preferences
    ) {
        // Preserve the initial request surface for existing route consumers
        public Request(
                @Nullable Level rootLevel,
                Location start,
                Location target,
                Safety safety,
                Movement movement,
                double nodeSpacing,
                double maximumRange,
                int maximumExpansions,
                double captureRadius,
                Validator validator
        ) {
            this(rootLevel, start, target, safety, movement, nodeSpacing,
                    maximumRange, maximumExpansions, captureRadius, validator,
                    RoutePreferences.DIRECT);
        }

        // Initialize the route planning request
        public Request {
            start = start == null ? Location.world(Vec3.ZERO) : start;
            target = target == null ? Location.world(Vec3.ZERO) : target;
            safety = safety == null ? Safety.DEFAULT : safety;
            movement = movement == null ? Movement.GROUND : movement;
            nodeSpacing = positive(nodeSpacing, 2.0D);
            maximumRange = Math.max(nodeSpacing, positive(maximumRange, 96.0D));
            maximumExpansions = Math.max(1, maximumExpansions);
            captureRadius = positive(captureRadius, Math.max(0.25D, nodeSpacing * 0.25D));
            validator = Objects.requireNonNull(validator, "validator");
            preferences = preferences == null ? RoutePreferences.DIRECT : preferences;
        }
    }

    // Select route cost and terminal-direction behavior without owning a vehicle controller
    public record RoutePreferences(
            Vec3 preferredForward,
            double distanceWeight,
            double lateralPenalty,
            double reversePenalty,
            double heuristicWeight,
            double minimumTerminalAlignment,
            double minimumForwardCommitDistance
    ) {
        // Preserve normal shortest-clear-route behavior
        public static final RoutePreferences DIRECT = new RoutePreferences(
                Vec3.ZERO, 1.0D, 0.0D, 0.0D, 1.0D, -1.0D, 0.0D);

        // Preserve the initial forward-first preference surface
        public RoutePreferences(
                Vec3 preferredForward,
                double distanceWeight,
                double lateralPenalty,
                double reversePenalty,
                double heuristicWeight,
                double minimumTerminalAlignment
        ) {
            this(preferredForward, distanceWeight, lateralPenalty, reversePenalty,
                    heuristicWeight, minimumTerminalAlignment, 0.0D);
        }

        // Initialize the route preference
        public RoutePreferences {
            preferredForward = finite(preferredForward)
                    && preferredForward.lengthSqr() > EPSILON
                    ? preferredForward.normalize() : Vec3.ZERO;
            distanceWeight = nonNegative(distanceWeight, 1.0D);
            lateralPenalty = nonNegative(lateralPenalty, 0.0D);
            reversePenalty = nonNegative(reversePenalty, 0.0D);
            heuristicWeight = nonNegative(heuristicWeight, 1.0D);
            minimumTerminalAlignment = Double.isFinite(minimumTerminalAlignment)
                    ? Math.max(-1.0D, Math.min(1.0D, minimumTerminalAlignment)) : -1.0D;
            minimumForwardCommitDistance = nonNegative(minimumForwardCommitDistance, 0.0D);
        }

        // Get the directional cost of one accepted route segment
        public double movementCost(Vec3 currentHeading, Vec3 start, Vec3 end, RouteMode mode) {
            Vec3 delta = end.subtract(start);
            double distance = delta.length();
            if (distance <= EPSILON) {
                return 0.0D;
            }
            double directionalPenalty = 0.0D;
            if (usesHeading()) {
                Vec3 heading = normalize(currentHeading, preferredForward);
                double alignment = Math.max(-1.0D,
                        Math.min(1.0D, heading.dot(delta.scale(1.0D / distance))));
                directionalPenalty = alignment >= 0.0D
                        ? lateralPenalty * (1.0D - alignment)
                        : lateralPenalty + reversePenalty * -alignment;
            }
            RouteMode selected = mode == null ? RouteMode.GROUND : mode;
            return distance * (distanceWeight * selected.costMultiplier() + directionalPenalty);
        }

        // Get the deliberately weak remaining-distance estimate
        public double heuristicCost(Vec3 from, Vec3 target) {
            return from.distanceTo(target) * heuristicWeight;
        }

        // Get the heading used for the first route segment
        public Vec3 initialHeading(Vec3 start, Vec3 target) {
            return normalize(preferredForward, target.subtract(start));
        }

        // Get the part of one segment which satisfies the initial forward commitment
        public double forwardProgress(Vec3 delta) {
            if (preferredForward.lengthSqr() <= EPSILON || !finite(delta)) {
                return 0.0D;
            }
            return Math.max(0.0D, preferredForward.dot(delta));
        }

        // Check whether the initial forward movement has been completed
        public boolean forwardCommitted(double forwardDistance) {
            return minimumForwardCommitDistance <= EPSILON
                    || forwardDistance + EPSILON >= minimumForwardCommitDistance;
        }

        // Keep a forward-first route from immediately reversing before it has room to turn
        public boolean allowsMovement(boolean forwardCommitted, Vec3 currentHeading,
                                      Vec3 travelDirection) {
            if (forwardCommitted || minimumForwardCommitDistance <= EPSILON) {
                return true;
            }
            return normalize(currentHeading, preferredForward).dot(travelDirection) > EPSILON;
        }

        // Check whether the route needs a heading-aware search state
        public boolean usesHeading() {
            return preferredForward.lengthSqr() > EPSILON;
        }

        // Check whether the final direct segment preserves the selected forward-first policy
        public boolean acceptsTerminal(Vec3 currentHeading, double forwardDistance,
                                       Vec3 from, Vec3 target, double captureRadius) {
            Vec3 delta = target.subtract(from);
            double distance = delta.length();
            if (distance <= Math.max(0.0D, captureRadius)
                    || !usesHeading()) {
                return true;
            }
            if (!forwardCommitted(forwardDistance)) {
                return false;
            }
            return normalize(currentHeading, preferredForward).dot(delta.scale(1.0D / distance))
                    >= minimumTerminalAlignment;
        }
    }

    // Store a full clearance envelope around the routed actor or machine
    public record Safety(double horizontalRadius, double height, double bottomOffset) {
        // Default single-block safety envelope
        public static final Safety DEFAULT = new Safety(0.5D, 1.0D, 0.5D);

        // Initialize the safety envelope
        public Safety {
            horizontalRadius = positive(horizontalRadius, 0.5D);
            height = positive(height, 1.0D);
            bottomOffset = Double.isFinite(bottomOffset)
                    ? Math.max(0.0D, Math.min(height, bottomOffset)) : height * 0.5D;
        }

        // Get the conservative world bounds at one route center
        public AABB boundsAt(Vec3 center) {
            Vec3 safeCenter = finite(center) ? center : Vec3.ZERO;
            return new AABB(safeCenter.x - horizontalRadius, safeCenter.y - bottomOffset,
                    safeCenter.z - horizontalRadius, safeCenter.x + horizontalRadius,
                    safeCenter.y - bottomOffset + height, safeCenter.z + horizontalRadius);
        }

    }

    // Select the movement media represented by the caller's traversal policy
    public enum RouteMode {
        // Surface travel with bounded one-grid-cell rises and descents
        GROUND(false, 1.0D),
        // Spatial travel through a caller-validated fluid volume
        WATER(true, 1.1D),
        // Spatial travel through caller-approved air volume
        FLIGHT(true, 1.0D);

        // Whether the route may change vertical grid coordinates freely
        private final boolean spatial;
        // Bias used when two otherwise safe routes have equal length
        private final double costMultiplier;

        // Initialize the route mode
        RouteMode(boolean spatial, double costMultiplier) {
            this.spatial = spatial;
            this.costMultiplier = costMultiplier;
        }

        // Check whether this mode is spatial
        public boolean spatial() {
            return spatial;
        }

        // Get the route cost multiplier
        public double costMultiplier() {
            return costMultiplier;
        }
    }

    // Store the available movement media and ground rise limit
    public record Movement(Set<RouteMode> modes, int maximumGroundRiseSteps) {
        // Standard ground movement
        public static final Movement GROUND = new Movement(Set.of(RouteMode.GROUND), 1);
        // Standard flight movement
        public static final Movement FLIGHT = new Movement(Set.of(RouteMode.FLIGHT), 0);
        // Standard water movement
        public static final Movement WATER = new Movement(Set.of(RouteMode.WATER), 0);

        // Initialize movement choices
        public Movement {
            EnumSet<RouteMode> selected = EnumSet.noneOf(RouteMode.class);
            if (modes != null) {
                for (RouteMode mode : modes) {
                    if (mode != null) {
                        selected.add(mode);
                    }
                }
            }
            if (selected.isEmpty()) {
                selected.add(RouteMode.GROUND);
            }
            modes = Set.copyOf(selected);
            maximumGroundRiseSteps = Math.max(0, maximumGroundRiseSteps);
        }
    }

    // Evaluate one prospective world-space traversal segment
    @FunctionalInterface
    public interface Validator {
        // Validate one full envelope sweep without changing world state
        Traversal validate(Query query);
    }

    // Store data supplied to one traversal policy
    public record Query(@Nullable Level rootLevel, Vec3 start, Vec3 end,
                        Safety safety, RouteMode mode) {
        // Initialize the traversal query
        public Query {
            start = finite(start) ? start : Vec3.ZERO;
            end = finite(end) ? end : Vec3.ZERO;
            safety = safety == null ? Safety.DEFAULT : safety;
            mode = mode == null ? RouteMode.GROUND : mode;
        }
    }

    // Store one policy decision
    public record Traversal(TraversalResult result, @Nullable RouteMode mode) {
        // Initialize the traversal decision
        public Traversal {
            result = result == null ? TraversalResult.BLOCKED : result;
        }

        // Create a clear result
        public static Traversal clear(RouteMode mode) {
            return new Traversal(TraversalResult.CLEAR, mode);
        }

        // Create a blocked result
        public static Traversal blocked() {
            return new Traversal(TraversalResult.BLOCKED, null);
        }

        // Create an unavailable result
        public static Traversal unavailable() {
            return new Traversal(TraversalResult.UNAVAILABLE, null);
        }
    }

    // Describe whether a candidate may safely be used right now
    public enum TraversalResult {
        CLEAR,
        BLOCKED,
        UNAVAILABLE
    }

    // Configure the built-in Sable collision validator
    public record CollisionOptions(boolean includeRootLevel,
                                   boolean includeTaggedTransparentBlocks,
                                   Set<UUID> excludedSubLevelIds,
                                   int maximumProbesPerBounds) {
        // Default collision behavior
        public static final CollisionOptions DEFAULT = new CollisionOptions(true, false, Set.of(), 16);

        // Initialize collision options
        public CollisionOptions {
            excludedSubLevelIds = excludedSubLevelIds == null ? Set.of()
                    : Set.copyOf(excludedSubLevelIds);
            maximumProbesPerBounds = Math.max(1, maximumProbesPerBounds);
        }
    }

    // Select the collision workload for an individual Sable route validator.
    public enum CollisionPrecision {
        // Use a bounded set of leading-face probes for high-frequency host checks.
        PROBED,
        // Sweep the complete safety envelope for authoritative route acceptance.
        SWEPT
    }

    // Configure how a supported ground actor excludes its resting-contact band.
    public record GroundContactPolicy(double contactClearance) {
        // Retain the normal full-envelope behavior.
        public static final GroundContactPolicy NONE = new GroundContactPolicy(0.0D);

        // Initialize a non-negative contact clearance.
        public GroundContactPolicy {
            contactClearance = Double.isFinite(contactClearance)
                    ? Math.max(0.0D, contactClearance) : 0.0D;
        }

        // Exclude only the lower supported-contact band from a ground envelope.
        public AABB removeSupportContact(AABB bounds) {
            if (bounds == null || contactClearance <= EPSILON) {
                return bounds;
            }
            double minimumY = Math.min(bounds.maxY - EPSILON,
                    bounds.minY + contactClearance);
            return new AABB(bounds.minX, minimumY, bounds.minZ,
                    bounds.maxX, bounds.maxY, bounds.maxZ);
        }
    }

    // Retain a route search so a caller can spread a complete plan across server ticks.
    public static final class QueuedPlan {
        private final Request request;
        private final @Nullable Vec3 origin;
        private final @Nullable Vec3 target;
        private final @Nullable State initial;
        private final Map<Node, State> states = new HashMap<>();
        private final Set<Node> closed = new HashSet<>();
        private final PriorityQueue<Entry> frontier = new PriorityQueue<>(
                Comparator.comparingDouble(Entry::score));
        private final List<Candidate> candidateMoves;
        private final ArrayDeque<DebugSegment> recentCheckedSegments = new ArrayDeque<>();
        private List<DebugSegment> debugCheckedSegments = List.of();
        private @Nullable State nearest;
        private boolean unavailable;
        private int expanded;
        private boolean finished;
        private Result result;

        // Initialize a queued plan from one stable pair of resolved endpoints.
        private QueuedPlan(Request request) {
            this.request = request;
            Vec3 start = request.start().resolve(request.rootLevel());
            origin = start;
            target = request.target().resolve(request.rootLevel());
            candidateMoves = candidates(request);
            if (start == null || target == null) {
                initial = null;
                result = Result.unavailable();
                finished = true;
                return;
            }
            if (start.distanceToSqr(target) <= request.captureRadius() * request.captureRadius()) {
                initial = null;
                Traversal terminal = traverse(request, start, target);
                recordCheckedSegment(start, target, terminal);
                Result immediate = terminal.result() == TraversalResult.CLEAR
                        ? new Result(Outcome.COMPLETE,
                        List.of(new Waypoint(target, terminal.mode())), 0)
                        : new Result(terminal.result() == TraversalResult.UNAVAILABLE
                        ? Outcome.UNAVAILABLE : Outcome.BLOCKED, List.of(), 0);
                finish(immediate);
                return;
            }
            Vec3 directDelta = target.subtract(start);
            Vec3 directHeading = normalize(directDelta,
                    request.preferences().initialHeading(start, target));
            double directForwardDistance = request.preferences().forwardProgress(directDelta);
            if (request.preferences().acceptsTerminal(directHeading, directForwardDistance,
                    start, target, request.captureRadius())) {
                Traversal terminal = traverse(request, start, target);
                recordCheckedSegment(start, target, terminal);
                if (terminal.result() == TraversalResult.CLEAR) {
                    initial = null;
                    finish(new Result(Outcome.COMPLETE,
                            List.of(new Waypoint(target, terminal.mode())), 0));
                    return;
                }
            }
            Node origin = new Node(0, 0, 0, false);
            initial = new State(start, request.preferences().initialHeading(start, target),
                    0.0D, 0.0D, false, null, null, null);
            nearest = initial;
            states.put(origin, initial);
            frontier.add(new Entry(origin, 0.0D, heuristic(request, start, target)));
            result = new Result(Outcome.LIMIT_REACHED, List.of(), 0);
        }

        // Advance the search by no more than the supplied node-expansion budget.
        public void advance(int maximumAdditionalExpansions) {
            if (finished) return;
            int budget = Math.max(1, maximumAdditionalExpansions);
            int limit = Math.min(request.maximumExpansions(), expanded + budget);
            while (!frontier.isEmpty() && expanded < limit) {
                Entry entry = frontier.poll();
                expanded++;
                State current = states.get(entry.node());
                if (current == null || entry.cost() > current.cost() + EPSILON || !closed.add(entry.node())) {
                    continue;
                }
                if (nearest == null || target == null) {
                    finish(Result.unavailable());
                    return;
                }
                if (current.position().distanceToSqr(target)
                        < nearest.position().distanceToSqr(target)) {
                    nearest = current;
                }
                if (request.preferences().acceptsTerminal(current.heading(), current.forwardDistance(),
                        current.position(), target, request.captureRadius())) {
                    Traversal terminal = traverse(request, current.position(), target);
                    recordCheckedSegment(current.position(), target, terminal);
                    if (terminal.result() == TraversalResult.CLEAR) {
                        finish(complete(request, current, target, terminal.mode(), expanded));
                        return;
                    }
                    unavailable |= terminal.result() == TraversalResult.UNAVAILABLE;
                }
                expand(entry, current);
            }
            if (frontier.isEmpty() || expanded >= request.maximumExpansions()) {
                finish(partialResult());
            } else {
                result = partialResult();
            }
        }

        // Check whether no further planner work remains.
        public boolean finished() {
            return finished;
        }

        // Get the latest safe partial route or the completed cached route.
        public Result result() {
            return result;
        }

        // Snapshot the safe partial route and the latest bounded set of checked search segments.
        public DebugRoute debugRoute(String id) {
            Vec3 debugOrigin = origin == null ? Vec3.ZERO : origin;
            Vec3 debugTarget = target == null ? debugOrigin : target;
            return new DebugRoute(id, debugOrigin, debugTarget,
                    result.waypoints(), result.outcome(), debugCheckedSegments);
        }

        // Get work completed against the caller-selected bounded search budget.
        public double progress() {
            return finished ? 1.0D : Math.min(0.999D,
                    expanded / (double) request.maximumExpansions());
        }

        // Add every valid successor of one accepted search state.
        private void expand(Entry entry, State current) {
            for (Candidate candidate : candidateMoves) {
                Vec3 nextPosition = current.position().add(candidate.step().offset()
                        .scale(request.nodeSpacing()));
                Vec3 travelDirection = normalize(nextPosition.subtract(current.position()),
                        current.heading());
                double forwardDistance = current.forwardDistance()
                        + request.preferences().forwardProgress(nextPosition.subtract(current.position()));
                boolean forwardCommitted = request.preferences().forwardCommitted(forwardDistance);
                if (!request.preferences().allowsMovement(current.forwardCommitted(),
                        current.heading(), travelDirection)) continue;
                Node next = entry.node().add(candidate.step(), travelDirection,
                        forwardCommitted, request.preferences().usesHeading());
                if (closed.contains(next) || !withinRange(next, request)) continue;
                // The node key includes the quantised heading. If the retained
                // state is already no more expensive, this edge cannot improve
                // the search. Reject it before the (intentionally exact) hull
                // sweep; doing the sweep first repeated the dominant collision
                // work around congested ground routes.
                double cost = current.cost() + request.preferences().movementCost(
                        current.heading(), current.position(), nextPosition, candidate.mode());
                State known = states.get(next);
                if (known != null && known.cost() <= cost + EPSILON) continue;
                Traversal traversal = request.validator().validate(new Query(
                        request.rootLevel(), current.position(), nextPosition,
                        request.safety(), candidate.mode()));
                recordCheckedSegment(current.position(), nextPosition, traversal);
                if (traversal == null || traversal.result() != TraversalResult.CLEAR) {
                    unavailable |= traversal != null && traversal.result() == TraversalResult.UNAVAILABLE;
                    continue;
                }
                State accepted = new State(nextPosition, travelDirection, cost, forwardDistance,
                        forwardCommitted, entry.node(), candidate.mode(), current);
                states.put(next, accepted);
                frontier.add(new Entry(next, cost, cost + heuristic(request, nextPosition, target)));
            }
        }

        // Build the latest valid partial path without marking an arrival.
        private Result partialResult() {
            if (nearest == null || nearest == initial) {
                return new Result(unavailable ? Outcome.UNAVAILABLE : Outcome.BLOCKED,
                        List.of(), expanded);
            }
            return new Result(unavailable ? Outcome.UNAVAILABLE : Outcome.LIMIT_REACHED,
                    buildPartial(nearest), expanded);
        }

        // Complete the retained search.
        private void finish(Result result) {
            this.result = result;
            // Search probes are only useful while work remains. A completed
            // cache renders as its accepted route alone.
            recentCheckedSegments.clear();
            debugCheckedSegments = List.of();
            finished = true;
        }

        // Retain a small rolling diagnostic sample without turning planner telemetry into retained world state.
        private void recordCheckedSegment(Vec3 start, Vec3 end, @Nullable Traversal traversal) {
            if (!finite(start) || !finite(end)) {
                return;
            }
            while (recentCheckedSegments.size() >= MAX_DEBUG_CHECKED_SEGMENTS) {
                recentCheckedSegments.removeFirst();
            }
            recentCheckedSegments.addLast(new DebugSegment(start, end,
                    traversal == null ? TraversalResult.BLOCKED : traversal.result()));
            debugCheckedSegments = List.copyOf(recentCheckedSegments);
        }
    }

    // Share a fixed planner-expansion allowance between callers for one supplied tick value.
    public static final class WorkBudget {
        private final int maximumWorkPerTick;
        private long currentTick = Long.MIN_VALUE;
        private int remainingWork;
        private final Map<Object, Long> ownerLastWorkTick = new IdentityHashMap<>();

        // Configure one positive fixed work allowance.
        public WorkBudget(int maximumWorkPerTick) {
            this.maximumWorkPerTick = Math.max(1, maximumWorkPerTick);
        }

        // Claim bounded planner work without exceeding this tick's allowance.
        public synchronized int claim(long tick, int requestedWork) {
            resetTick(tick);
            int claimed = Math.min(Math.max(0, requestedWork), remainingWork);
            remainingWork -= claimed;
            return claimed;
        }

        // Claim bounded work fairly among retained callers that share this budget.
        public synchronized int claim(Object owner, long tick, int requestedWork) {
            if (owner == null) return claim(tick, requestedWork);
            resetTick(tick);
            ownerLastWorkTick.putIfAbsent(owner, Long.MIN_VALUE);
            long ownerLastTick = ownerLastWorkTick.get(owner);
            for (long lastWorkTick : ownerLastWorkTick.values()) {
                if (lastWorkTick < ownerLastTick) return 0;
            }
            int claimed = Math.min(Math.max(0, requestedWork), remainingWork);
            if (claimed > 0) {
                remainingWork -= claimed;
                ownerLastWorkTick.put(owner, tick);
            }
            return claimed;
        }

        // Stop retaining a caller which no longer has queued planning work.
        public synchronized void release(Object owner) {
            if (owner != null) ownerLastWorkTick.remove(owner);
        }

        // Reset the per-tick allowance when the caller advances the game clock.
        private void resetTick(long tick) {
            if (currentTick != tick) {
                currentTick = tick;
                remainingWork = maximumWorkPerTick;
            }
        }

        // Get the fixed allowance configured for each tick.
        public int maximumWorkPerTick() {
            return maximumWorkPerTick;
        }
    }

    // Report waypoint cursor movement caused by the physical hull crossing a checkpoint.
    public record WaypointAdvance(int nextWaypointIndex, int skippedWaypoints) {
        // Initialize the advancement result.
        public WaypointAdvance {
            nextWaypointIndex = Math.max(0, nextWaypointIndex);
            skippedWaypoints = Math.max(0, skippedWaypoints);
        }
    }

    // Identify the next waypoint after the retained leg nearest a live position.
    public record RouteCursor(int nextWaypointIndex, double distanceToRouteSqr) {
        // Retain the original cursor constructor for integrations which only need the checkpoint.
        public RouteCursor(int nextWaypointIndex) {
            this(nextWaypointIndex, Double.POSITIVE_INFINITY);
        }

        // Normalize cursor metadata supplied by integrations.
        public RouteCursor {
            nextWaypointIndex = Math.max(-1, nextWaypointIndex);
            distanceToRouteSqr = Double.isFinite(distanceToRouteSqr)
                    ? Math.max(0.0D, distanceToRouteSqr) : Double.POSITIVE_INFINITY;
        }

        // Create an empty cursor result.
        public static RouteCursor none() {
            return new RouteCursor(-1, Double.POSITIVE_INFINITY);
        }

        // Check whether this cursor identifies a route checkpoint.
        public boolean found() {
            return nextWaypointIndex >= 0;
        }
    }

    // Locate the nearest point on one remaining route leg.
    public record RouteProjection(
            int nextWaypointIndex,
            Vec3 position,
            double legProgress,
            double distanceToRouteSqr
    ) {
        // Normalize projected route geometry.
        public RouteProjection {
            nextWaypointIndex = Math.max(-1, nextWaypointIndex);
            position = finite(position) ? position : Vec3.ZERO;
            legProgress = Double.isFinite(legProgress) ? legProgress : 0.0D;
            distanceToRouteSqr = Double.isFinite(distanceToRouteSqr)
                    ? Math.max(0.0D, distanceToRouteSqr) : Double.POSITIVE_INFINITY;
        }

        // Create an empty route projection.
        public static RouteProjection none() {
            return new RouteProjection(
                    -1, Vec3.ZERO, 0.0D, Double.POSITIVE_INFINITY);
        }

        // Check whether a retained route leg was found.
        public boolean found() {
            return nextWaypointIndex >= 0;
        }
    }

    // Store safe-route geometry oriented toward the caller-owned destination.
    public record OrientedRoute(
            Vec3 origin,
            Vec3 target,
            List<Waypoint> waypoints,
            boolean reversed
    ) {
        // Normalize oriented route geometry.
        public OrientedRoute {
            origin = finite(origin) ? origin : Vec3.ZERO;
            target = finite(target) ? target : origin;
            waypoints = waypoints == null ? List.of() : List.copyOf(waypoints);
        }

        // Create an empty oriented route.
        public static OrientedRoute none() {
            return new OrientedRoute(
                    Vec3.ZERO, Vec3.ZERO, List.of(), false);
        }

        // Check whether reusable route geometry matched the destination.
        public boolean found() {
            return !waypoints.isEmpty();
        }
    }

    // Store one directed, independently validated leg for reusable route-graph stitching.
    public record RouteLeg(Vec3 origin, Vec3 target, List<Waypoint> waypoints) {
        // Normalize route graph input.
        public RouteLeg {
            origin = finite(origin) ? origin : Vec3.ZERO;
            target = finite(target) ? target : Vec3.ZERO;
            waypoints = waypoints == null ? List.of() : List.copyOf(waypoints);
        }
    }

    // Store one connected path through a route graph ending at the requested terminal leg.
    public record StitchedRoute(
            Vec3 origin,
            Vec3 target,
            List<Waypoint> waypoints,
            int legCount,
            boolean closed
    ) {
        // Normalize stitched route output.
        public StitchedRoute {
            origin = finite(origin) ? origin : Vec3.ZERO;
            target = finite(target) ? target : Vec3.ZERO;
            waypoints = waypoints == null ? List.of() : List.copyOf(waypoints);
            legCount = Math.max(0, legCount);
        }

        // Check whether a connected route was produced.
        public boolean found() {
            return legCount > 0 && !waypoints.isEmpty();
        }

        // Get an empty graph result.
        public static StitchedRoute none() {
            return new StitchedRoute(Vec3.ZERO, Vec3.ZERO, List.of(), 0, false);
        }
    }

    // Store a coarse route endpoint which hands control back inside its capture area.
    public record RouteTerminal(Vec3 position, double captureRadius) {
        // Normalize route terminal geometry.
        public RouteTerminal {
            position = finite(position) ? position : Vec3.ZERO;
            captureRadius = nonNegative(captureRadius, 0.0D);
        }

        // Check whether live control may take over from the retained route.
        public boolean reached(Vec3 currentPosition) {
            return finite(currentPosition) && currentPosition.distanceToSqr(position)
                    <= captureRadius * captureRadius;
        }
    }

    // Identify a remaining route waypoint which can be reached or safely rejoined by a local plan.
    public record RouteRejoin(int waypointIndex, boolean directlyReachable) {
        // Create an empty rejoin decision.
        public static RouteRejoin none() {
            return new RouteRejoin(-1, false);
        }

        // Check whether a valid route waypoint was found.
        public boolean found() {
            return waypointIndex >= 0;
        }
    }

    // Store one bounded retained-route rejoin scan result.
    public record RouteRejoinScan(RouteRejoin rejoin, int nextWaypointIndex,
                                  boolean exhausted) {
        // Initialize a scan result.
        public RouteRejoinScan {
            rejoin = rejoin == null ? RouteRejoin.none() : rejoin;
            nextWaypointIndex = Math.max(0, nextWaypointIndex);
        }

        // Create a scan result with no route to inspect.
        public static RouteRejoinScan empty() {
            return new RouteRejoinScan(RouteRejoin.none(), 0, true);
        }

        // Check whether this pass found a safe rejoin leg.
        public boolean found() {
            return rejoin.found();
        }
    }

    // Identify a projected point on a retained leg which can accept live control.
    public record RouteLegRejoin(
            int waypointIndex,
            Vec3 position,
            boolean directlyReachable
    ) {
        // Normalize a route-leg rejoin result.
        public RouteLegRejoin {
            waypointIndex = Math.max(-1, waypointIndex);
            position = finite(position) ? position : Vec3.ZERO;
        }

        // Create an empty route-leg rejoin result.
        public static RouteLegRejoin none() {
            return new RouteLegRejoin(-1, Vec3.ZERO, false);
        }

        // Check whether a projected rejoin point was found.
        public boolean found() {
            return waypointIndex >= 0;
        }
    }

    // Store one bounded projected route-leg rejoin scan result.
    public record RouteLegRejoinScan(
            RouteLegRejoin rejoin,
            int nextWaypointIndex,
            boolean exhausted
    ) {
        // Normalize a projected rejoin scan.
        public RouteLegRejoinScan {
            rejoin = rejoin == null ? RouteLegRejoin.none() : rejoin;
            nextWaypointIndex = Math.max(0, nextWaypointIndex);
        }

        // Create a projected scan result with no route to inspect.
        public static RouteLegRejoinScan empty() {
            return new RouteLegRejoinScan(
                    RouteLegRejoin.none(), 0, true);
        }

        // Check whether this pass found a projected rejoin point.
        public boolean found() {
            return rejoin.found();
        }
    }

    // Store the planner result; non-complete routes are safe partial approaches, never arrivals
    public record Result(Outcome outcome, List<Waypoint> waypoints, int expandedNodes) {
        // Initialize the planning result
        public Result {
            outcome = outcome == null ? Outcome.BLOCKED : outcome;
            waypoints = waypoints == null ? List.of() : List.copyOf(waypoints);
            expandedNodes = Math.max(0, expandedNodes);
        }

        // Create an unavailable result
        private static Result unavailable() {
            return new Result(Outcome.UNAVAILABLE, List.of(), 0);
        }

        // Check whether the final destination was reached
        public boolean reachedDestination() {
            return outcome == Outcome.COMPLETE;
        }
    }

    // Store a detached route snapshot for a client or diagnostic integration.
    // The planner does not retain, synchronize or render these snapshots.
    public record DebugRoute(String id, Vec3 origin, Vec3 target,
                             List<Waypoint> waypoints, Outcome outcome,
                             List<DebugSegment> checkedSegments,
                             boolean targetLegValidated,
                             DebugRouteStyle style) {
        // Preserve the original route-only diagnostic snapshot constructor.
        public DebugRoute(String id, Vec3 origin, Vec3 target,
                          List<Waypoint> waypoints, Outcome outcome) {
            this(id, origin, target, waypoints, outcome, List.of(),
                    outcome == Outcome.COMPLETE, DebugRouteStyle.LIVE);
        }

        // Preserve the previous checked-segment constructor.
        public DebugRoute(String id, Vec3 origin, Vec3 target,
                          List<Waypoint> waypoints, Outcome outcome,
                          List<DebugSegment> checkedSegments) {
            this(id, origin, target, waypoints, outcome, checkedSegments,
                    outcome == Outcome.COMPLETE, DebugRouteStyle.LIVE);
        }

        // Preserve the previous explicit-target-leg constructor.
        public DebugRoute(String id, Vec3 origin, Vec3 target,
                          List<Waypoint> waypoints, Outcome outcome,
                          List<DebugSegment> checkedSegments,
                          boolean targetLegValidated) {
            this(id, origin, target, waypoints, outcome, checkedSegments,
                    targetLegValidated, DebugRouteStyle.LIVE);
        }

        // Initialize the diagnostic route snapshot
        public DebugRoute {
            id = id == null ? "" : id;
            origin = finite(origin) ? origin : Vec3.ZERO;
            target = finite(target) ? target : origin;
            waypoints = waypoints == null ? List.of() : List.copyOf(waypoints);
            outcome = outcome == null ? Outcome.BLOCKED : outcome;
            checkedSegments = checkedSegments == null ? List.of() : List.copyOf(checkedSegments);
            targetLegValidated &= outcome == Outcome.COMPLETE;
            style = style == null ? DebugRouteStyle.LIVE : style;
        }
    }

    // Select a diagnostic route's meaning without coupling the planner to a particular transport or UI.
    public enum DebugRouteStyle {
        // A current vehicle route or live planner result; use outcome colours.
        LIVE,
        // A completed, retained route cache; render distinctly from live steering.
        CACHED
    }

    // Describe one bounded planner segment validation for diagnostic rendering only.
    public record DebugSegment(Vec3 start, Vec3 end, TraversalResult result) {
        // Initialize a finite diagnostic segment.
        public DebugSegment {
            start = finite(start) ? start : Vec3.ZERO;
            end = finite(end) ? end : start;
            result = result == null ? TraversalResult.BLOCKED : result;
        }
    }

    // Describe how a route search ended
    public enum Outcome {
        COMPLETE,
        BLOCKED,
        UNAVAILABLE,
        LIMIT_REACHED
    }

    // Store one world-space control waypoint and the validated traversal medium
    public record Waypoint(Vec3 position, @Nullable RouteMode mode) {
        // Initialize the route waypoint
        public Waypoint {
            position = finite(position) ? position : Vec3.ZERO;
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                         HELPERS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Store one nearest point on a complete graph leg.
    private record RoutePolylineProjection(
            int segmentIndex,
            Vec3 position,
            double distanceToRouteSqr,
            double distanceFromOrigin,
            double totalLength
    ) {
        private static RoutePolylineProjection none() {
            return new RoutePolylineProjection(
                    -1, Vec3.ZERO, Double.POSITIVE_INFINITY, 0.0D, 0.0D);
        }

        private boolean found() {
            return segmentIndex >= 0;
        }
    }

    // Store one successful shortest continuation through unused graph legs.
    private record GraphContinuation(
            boolean found,
            List<Waypoint> waypoints,
            double distance
    ) {
        private static GraphContinuation complete() {
            return new GraphContinuation(true, List.of(), 0.0D);
        }

        private static GraphContinuation none() {
            return new GraphContinuation(
                    false, List.of(), Double.POSITIVE_INFINITY);
        }
    }

    // Store one deterministic shortest-path frontier entry over route-leg endpoints.
    private record GraphSearchNode(
            int endpointIndex,
            double distance,
            List<Waypoint> waypoints
    ) {
    }

    // Rank graph routes by nearest physical leg first, then remaining travel distance.
    private record RouteGraphCandidate(
            Vec3 origin,
            Vec3 target,
            List<Waypoint> waypoints,
            boolean reversed,
            double distanceToRouteSqr,
            double distance
    ) {
        private static RouteGraphCandidate none() {
            return new RouteGraphCandidate(
                    Vec3.ZERO, Vec3.ZERO, List.of(), false,
                    Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        }

        private boolean found() {
            return !waypoints.isEmpty();
        }

        private boolean betterThan(RouteGraphCandidate other) {
            if (other == null || !other.found()) return found();
            return distanceToRouteSqr < other.distanceToRouteSqr - EPSILON
                    || Math.abs(distanceToRouteSqr - other.distanceToRouteSqr) <= EPSILON
                    && distance + EPSILON < other.distance;
        }
    }

    // Store one grid coordinate
    private record Node(int x, int y, int z, boolean forwardCommitted) {
        // Add one grid step
        private Node add(GridStep step, Vec3 heading, boolean committed,
                         boolean retainHeading) {
            // Heading biases route cost but cannot change whether the swept
            // hull clears this cell. Keeping it in the key retraced the same
            // blocked neighbourhood once for each arrival direction.
            return new Node(x + step.x(), y + step.y(), z + step.z(),
                    retainHeading && committed);
        }
    }

    // Store one grid displacement
    private record GridStep(int x, int y, int z) {
        // Convert the step to a world-space offset
        private Vec3 offset() {
            return new Vec3(x, y, z);
        }
    }

    // Store one candidate move
    private record Candidate(GridStep step, RouteMode mode) {
    }

    // Store one accepted A* state
    private record State(Vec3 position, Vec3 heading, double cost, double forwardDistance,
                         boolean forwardCommitted, @Nullable Node parent,
                         @Nullable RouteMode mode, @Nullable State previous) {
    }

    // Store one queued A* state
    private record Entry(Node node, double cost, double score) {
    }
}
