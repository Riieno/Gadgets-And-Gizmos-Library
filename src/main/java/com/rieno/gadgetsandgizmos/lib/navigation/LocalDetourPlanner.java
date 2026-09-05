package com.rieno.gadgetsandgizmos.lib.navigation;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

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

// Find a small sparse detour around an obstacle near a direct route
public final class LocalDetourPlanner {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the local detour planner
    private LocalDetourPlanner() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Find one bounded local detour
    public static List<Vec3> plan(Request request) {
        Objects.requireNonNull(request, "request");
        if (request.segmentValidator().isClear(request.start(), request.target())) {
            return List.of(request.target());
        }

        Node origin = new Node(0, 0, 0);
        PriorityQueue<Entry> frontier = new PriorityQueue<>(
                Comparator.comparingDouble(Entry::score));
        Map<Node, Double> costs = new HashMap<>();
        Map<Node, Node> parents = new HashMap<>();
        Set<Node> closed = new HashSet<>();
        costs.put(origin, 0.0D);
        frontier.add(new Entry(origin, 0.0D,
                request.start().distanceTo(request.target())));

        List<Node> offsets = neighborOffsets(request.movementModel());
        int expansions = 0;
        while (!frontier.isEmpty() && expansions++ < request.maxExpansions()) {
            Entry entry = frontier.poll();
            double currentCost = costs.getOrDefault(
                    entry.node(), Double.POSITIVE_INFINITY);
            if (entry.cost() > currentCost + 1.0E-9D
                    || !closed.add(entry.node())) {
                continue;
            }
            Vec3 current = position(request, entry.node());
            if (!entry.node().equals(origin)
                    && request.segmentValidator().isClear(
                    current, request.target())) {
                return smooth(request, build(request, entry.node(), parents));
            }

            for (Node offset : offsets) {
                Node next = entry.node().add(offset);
                if (!withinBounds(next, request) || closed.contains(next)) continue;
                Vec3 nextPosition = position(request, next);
                if (!request.segmentValidator().isClear(current, nextPosition)) continue;
                double moveCost = current.distanceTo(nextPosition)
                        * movementPenalty(offset);
                double cost = currentCost + moveCost;
                if (cost + 1.0E-9D >= costs.getOrDefault(
                        next, Double.POSITIVE_INFINITY)) {
                    continue;
                }
                costs.put(next, cost);
                parents.put(next, entry.node());
                frontier.add(new Entry(next, cost,
                        cost + nextPosition.distanceTo(request.target())));
            }
        }
        return List.of();
    }

    // Get the available local moves
    private static List<Node> neighborOffsets(MovementModel movementModel) {
        List<Node> offsets = new ArrayList<>(List.of(
                new Node(1, 0, 0),
                new Node(2, 1, 0),
                new Node(2, -1, 0),
                new Node(1, 1, 0),
                new Node(1, -1, 0)));
        if (movementModel == MovementModel.PLANAR
                || movementModel == MovementModel.SPATIAL) {
            offsets.add(new Node(0, 1, 0));
            offsets.add(new Node(0, -1, 0));
        }
        if (movementModel == MovementModel.SPATIAL) {
            offsets.add(new Node(0, 0, 1));
            offsets.add(new Node(0, 0, -1));
        }
        if (movementModel == MovementModel.SPATIAL_STEERING
                || movementModel == MovementModel.SPATIAL) {
            offsets.add(new Node(1, 0, 1));
            offsets.add(new Node(1, 0, -1));
        }
        return List.copyOf(offsets);
    }

    // Check whether a node remains inside the small search window
    private static boolean withinBounds(Node node, Request request) {
        return node.forward() >= 0
                && node.forward() <= request.maxForwardSteps()
                && Math.abs(node.side()) <= request.maxSideSteps()
                && Math.abs(node.vertical()) <= request.maxVerticalSteps()
                && !node.equals(new Node(0, 0, 0));
    }

    // Get one node's world position
    private static Vec3 position(Request request, Node node) {
        return request.start()
                .add(request.forward().scale(
                        node.forward() * request.stepDistance()))
                .add(request.side().scale(
                        node.side() * request.stepDistance()))
                .add(request.vertical().scale(
                        node.vertical() * request.stepDistance()));
    }

    // Get one local movement penalty
    private static double movementPenalty(Node offset) {
        if (offset.vertical() < 0) return 1.2D;
        if (offset.vertical() > 0) return 1.1D;
        if (offset.forward() == 0) return 1.05D;
        return 1.0D;
    }

    // Build the sparse route
    private static List<Vec3> build(
            Request request,
            Node destination,
            Map<Node, Node> parents
    ) {
        ArrayDeque<Vec3> reversed = new ArrayDeque<>();
        Node cursor = destination;
        while (cursor != null && !cursor.equals(new Node(0, 0, 0))) {
            reversed.addFirst(position(request, cursor));
            cursor = parents.get(cursor);
        }
        List<Vec3> route = new ArrayList<>(reversed);
        if (route.isEmpty()
                || route.getLast().distanceToSqr(request.target()) > 1.0E-9D) {
            route.add(request.target());
        }
        return List.copyOf(route);
    }

    // Remove every waypoint which has a clear later segment
    private static List<Vec3> smooth(Request request, List<Vec3> route) {
        if (route.size() < 2) return route;
        List<Vec3> result = new ArrayList<>();
        Vec3 anchor = request.start();
        int next = 0;
        while (next < route.size()) {
            int furthest = next;
            for (int candidate = route.size() - 1; candidate > next; candidate--) {
                if (request.segmentValidator().isClear(
                        anchor, route.get(candidate))) {
                    furthest = candidate;
                    break;
                }
            }
            Vec3 waypoint = route.get(furthest);
            result.add(waypoint);
            anchor = waypoint;
            next = furthest + 1;
        }
        return List.copyOf(result);
    }

    // Store one bounded detour request
    public record Request(
            Vec3 start,
            Vec3 target,
            Vec3 forward,
            Vec3 side,
            Vec3 vertical,
            double stepDistance,
            int maxForwardSteps,
            int maxSideSteps,
            int maxVerticalSteps,
            int maxExpansions,
            MovementModel movementModel,
            SegmentValidator segmentValidator
    ) {
        // Initialize the detour request
        public Request {
            start = finite(start);
            target = finite(target);
            forward = normalize(forward, target.subtract(start));
            side = normalize(side, new Vec3(1.0D, 0.0D, 0.0D));
            vertical = normalize(vertical, new Vec3(0.0D, 1.0D, 0.0D));
            stepDistance = positive(stepDistance, 1.0D);
            maxForwardSteps = Math.max(1, maxForwardSteps);
            maxSideSteps = Math.max(1, maxSideSteps);
            maxVerticalSteps = Math.max(0, maxVerticalSteps);
            maxExpansions = Math.max(1, maxExpansions);
            movementModel = Objects.requireNonNull(
                    movementModel, "movementModel");
            segmentValidator = Objects.requireNonNull(
                    segmentValidator, "segmentValidator");
        }
    }

    // Select the local movements a vehicle can physically command
    public enum MovementModel {
        // Forward motion with steering, but no stationary lateral movement
        STEERING,
        // Independent forward and lateral movement on one plane
        PLANAR,
        // Forward steering with climb and descent, but no stationary movement
        SPATIAL_STEERING,
        // Independent forward, lateral and vertical movement
        SPATIAL
    }

    // Validate one complete hull segment in the host world
    @FunctionalInterface
    public interface SegmentValidator {
        // Check whether the segment is clear
        boolean isClear(Vec3 start, Vec3 end);
    }

    // Store one grid node
    private record Node(int forward, int side, int vertical) {
        // Add a node offset
        private Node add(Node value) {
            return new Node(
                    forward + value.forward,
                    side + value.side,
                    vertical + value.vertical);
        }
    }

    // Store one pending node
    private record Entry(Node node, double cost, double score) {
    }

    // Normalize one vector
    private static Vec3 normalize(Vec3 val, Vec3 fallback) {
        Vec3 safe = finite(val);
        if (safe.lengthSqr() > 1.0E-12D) return safe.normalize();
        safe = finite(fallback);
        return safe.lengthSqr() > 1.0E-12D ? safe.normalize() : Vec3.ZERO;
    }

    // Normalize one vector
    private static Vec3 finite(Vec3 val) {
        return val == null || !Double.isFinite(val.x)
                || !Double.isFinite(val.y) || !Double.isFinite(val.z)
                ? Vec3.ZERO : val;
    }

    // Normalize one positive value
    private static double positive(double val, double fallback) {
        return Double.isFinite(val) && val > 0.0D ? val : fallback;
    }
}
