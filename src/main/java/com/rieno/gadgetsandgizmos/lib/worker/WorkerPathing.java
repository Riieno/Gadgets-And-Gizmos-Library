package com.rieno.gadgetsandgizmos.lib.worker;

import com.rieno.gadgetsandgizmos.lib.navigation.SablePathfinder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// Create live Sable path searches with the standard worker clearance envelope
public final class WorkerPathing {
    private static final SablePathfinder.Safety WORKER_SAFETY =
            new SablePathfinder.Safety(0.35D, 1.9D, 0.05D);

    // Initialize worker pathing
    private WorkerPathing() {
    }

    // Queue a bounded ground route without loading absent chunks or sub-levels
    public static SablePathfinder.QueuedPlan queue(Level rootLevel, Vec3 start, Vec3 target,
                                                   double maximumRange) {
        return queue(rootLevel, start, target, maximumRange, false);
    }

    // Queue a live route which may use clear air when the worker has a flight-granting equipment effect.
    public static SablePathfinder.QueuedPlan queue(Level rootLevel, Vec3 start, Vec3 target,
                                                    double maximumRange, boolean allowFlight) {
        SablePathfinder.Validator collision = SablePathfinder.sableCollisionValidator(
                SablePathfinder.CollisionOptions.DEFAULT,
                new SablePathfinder.GroundContactPolicy(0.1D),
                SablePathfinder.CollisionPrecision.SWEPT);
        SablePathfinder.Validator validator = query -> {
            SablePathfinder.Traversal direct = collision.validate(query);
            if (direct.result() == SablePathfinder.TraversalResult.CLEAR
                    || query.mode() != SablePathfinder.RouteMode.GROUND
                    || !isOneBlockStep(query, collision)) return direct;
            return SablePathfinder.Traversal.clear(SablePathfinder.RouteMode.GROUND);
        };
        SablePathfinder.Movement movement = allowFlight
                ? new SablePathfinder.Movement(EnumSet.of(SablePathfinder.RouteMode.GROUND,
                SablePathfinder.RouteMode.FLIGHT), 1)
                : SablePathfinder.Movement.GROUND;
        return SablePathfinder.queue(new SablePathfinder.Request(
                rootLevel,
                SablePathfinder.Location.world(start),
                SablePathfinder.Location.world(target),
                WORKER_SAFETY,
                movement,
                0.75D,
                Math.max(8.0D, maximumRange),
                4096,
                0.6D,
                validator));
    }

    // Create a navigator which continually rebuilds short safe path prefixes from the live worker position
    public static LiveNavigator liveNavigator(double maximumRange) {
        return new LiveNavigator(maximumRange, false);
    }

    // Create a navigator which may select grounded or flight route legs.
    public static LiveNavigator liveNavigator(double maximumRange, boolean allowFlight) {
        return new LiveNavigator(maximumRange, allowFlight);
    }

    // Find the closest loaded ground position beside an interaction target
    public static Vec3 interactionPosition(Level level, BlockPos target, Vec3 origin) {
        if (level == null || target == null) return origin == null ? Vec3.ZERO : origin;
        Vec3 res = reachableInteractionPosition(level, List.of(target), null, origin);
        return res == null ? Vec3.atBottomCenterOf(target.above()) : res;
    }

    // Find a reachable exterior face of one linked block or multiblock endpoint
    public static @Nullable Vec3 reachableInteractionPosition(Level level, Collection<BlockPos> blocks,
                                                               @Nullable Direction preferredFace, Vec3 origin) {
        if (level == null || blocks == null || blocks.isEmpty()) return null;
        Set<BlockPos> members = new LinkedHashSet<>();
        for (BlockPos block : blocks) {
            if (block != null && level.isLoaded(block)) members.add(block.immutable());
        }
        if (members.isEmpty()) return null;
        Vec3 start = origin == null ? Vec3.ZERO : origin;
        List<Direction> faces = interactionFaces(preferredFace);
        Set<BlockPos> candidates = new LinkedHashSet<>();
        for (Direction face : faces) {
            for (BlockPos member : members) {
                BlockPos column = member.relative(face);
                for (int vertical = -1; vertical <= 1; vertical++) {
                    BlockPos candidate = column.offset(0, vertical, 0);
                    if (!members.contains(candidate) && canStandAt(level, candidate)) candidates.add(candidate);
                }
            }
            if (face == preferredFace && !candidates.isEmpty()) {
                return candidates.stream().map(Vec3::atBottomCenterOf)
                        .min(Comparator.comparingDouble(start::distanceToSqr)).orElse(null);
            }
        }
        return candidates.stream().map(Vec3::atBottomCenterOf)
                .min(Comparator.comparingDouble(start::distanceToSqr)).orElse(null);
    }

    // Get one loaded supported standing position for live worker navigation
    public static @Nullable Vec3 standingPosition(Level level, BlockPos pos) {
        if (level == null || pos == null || !canStandAt(level, pos)) return null;
        return Vec3.atBottomCenterOf(pos);
    }

    // Advance horizontally before applying one validated ground step
    public static Vec3 advanceGround(Vec3 current, Vec3 target, double maximumDistance) {
        Vec3 start = current == null ? Vec3.ZERO : current;
        Vec3 end = target == null ? start : target;
        double speed = Double.isFinite(maximumDistance) ? Math.max(0.0D, maximumDistance) : 0.0D;
        double x = end.x - start.x;
        double z = end.z - start.z;
        double horizontal = Math.sqrt(x * x + z * z);
        if (horizontal <= speed || horizontal <= 1.0E-9D) return end;
        double scale = speed / horizontal;
        return new Vec3(start.x + x * scale, start.y, start.z + z * scale);
    }

    // Advance one walking step while keeping the worker's feet on the current supported surface
    public static Vec3 advanceGround(Level level, Vec3 current, Vec3 target, double maximumDistance) {
        Vec3 start = current == null ? Vec3.ZERO : current;
        Vec3 end = target == null ? start : target;
        double speed = Double.isFinite(maximumDistance) ? Math.max(0.0D, maximumDistance) : 0.0D;
        double x = end.x - start.x;
        double z = end.z - start.z;
        double horizontal = Math.sqrt(x * x + z * z);
        if (horizontal <= 1.0E-9D || speed <= 0.0D) return start;
        double scale = Math.min(1.0D, speed / horizontal);
        double nextX = start.x + x * scale;
        double nextZ = start.z + z * scale;
        double nextY = supportedY(level, nextX, nextZ, start.y, end.y);
        if (Double.isNaN(nextY)) {
            double jump = Math.max(-0.25D, Math.min(0.42D, end.y - start.y));
            return new Vec3(nextX, start.y + jump, nextZ);
        }
        return new Vec3(nextX, nextY, nextZ);
    }

    // Keep one worker moving through short, current pathfinder prefixes instead of retaining a full route
    public static final class LiveNavigator {
        private final double maximumRange;
        private final boolean allowFlight;
        private SablePathfinder.QueuedPlan planner;
        private List<SablePathfinder.Waypoint> route = List.of();
        private int routeIndex;
        private Vec3 target = Vec3.ZERO;

        // Initialize the bounded live navigator
        private LiveNavigator(double maximumRange, boolean allowFlight) {
            this.maximumRange = Math.max(8.0D, maximumRange);
            this.allowFlight = allowFlight;
        }

        // Find and follow one current safe prefix toward the supplied live destination
        public NavigationStep advance(Level level, Vec3 current, Vec3 destination,
                                      int workBudget, double movementSpeed) {
            Vec3 position = current == null ? Vec3.ZERO : current;
            Vec3 nextDestination = destination == null ? position : destination;
            if (arrivalDistanceSqr(position, nextDestination, allowFlight) <= 1.0E-6D) {
                clear();
                return new NavigationStep(NavigationState.ARRIVED, position);
            }
            if (target.distanceToSqr(nextDestination) > 0.0625D) {
                clear();
                target = nextDestination;
            }
            if (routeIndex < route.size()) {
                SablePathfinder.Waypoint routeWaypoint = route.get(routeIndex);
                Vec3 waypoint = routeWaypoint.position();
                boolean flying = routeWaypoint.mode() == SablePathfinder.RouteMode.FLIGHT;
                Vec3 advanced = flying ? advanceFlight(position, waypoint, movementSpeed)
                        : advanceGround(level, position, waypoint, movementSpeed);
                if (arrivalDistanceSqr(advanced, waypoint, flying) <= 1.0E-6D) routeIndex++;
                if (routeIndex >= route.size()) {
                    route = List.of();
                    routeIndex = 0;
                    if (arrivalDistanceSqr(advanced, nextDestination, allowFlight) <= 1.0E-6D) {
                        clear();
                        return new NavigationStep(NavigationState.ARRIVED, advanced);
                    }
                }
                return new NavigationStep(NavigationState.NAVIGATING, advanced);
            }
            if (planner == null) planner = queue(level, position, nextDestination, maximumRange, allowFlight);
            planner.advance(Math.max(1, workBudget));
            SablePathfinder.Result result = planner.result();
            if (!result.waypoints().isEmpty()) {
                route = result.waypoints();
                routeIndex = 0;
                planner = null;
                return new NavigationStep(NavigationState.NAVIGATING, position);
            }
            if (planner.finished()) {
                planner = null;
                return new NavigationStep(NavigationState.UNAVAILABLE, position);
            }
            return new NavigationStep(NavigationState.NAVIGATING, position);
        }

        // Clear the transient path prefix when a worker changes destination
        public void clear() {
            planner = null;
            route = List.of();
            routeIndex = 0;
        }
    }

    // Describe a live navigation update
    public record NavigationStep(NavigationState state, Vec3 position) {
        // Check whether this update reached the requested destination
        public boolean arrived() {
            return state == NavigationState.ARRIVED;
        }

        // Check whether no currently loaded safe path prefix exists
        public boolean unavailable() {
            return state == NavigationState.UNAVAILABLE;
        }
    }

    // Define the state of one live navigation update
    public enum NavigationState {
        NAVIGATING,
        ARRIVED,
        UNAVAILABLE
    }

    // Find a supported stand height near the current route level without allowing a floating movement step
    private static double supportedY(Level level, double x, double z, double currentY, double targetY) {
        if (level == null || !Double.isFinite(x) || !Double.isFinite(z)) return Double.NaN;
        int minY = Math.max(level.getMinBuildHeight(), (int) Math.floor(Math.min(currentY, targetY)) - 2);
        int maxY = Math.min(level.getMaxBuildHeight() - 1,
                (int) Math.floor(Math.max(currentY, targetY)) + 2);
        double preferredY = Math.max(currentY - 1.25D, Math.min(currentY + 1.05D, targetY));
        double selectedY = Double.NaN;
        double selectedDistance = Double.MAX_VALUE;
        for (int y = minY; y <= maxY; y++) {
            BlockPos supportPos = BlockPos.containing(x, y, z);
            if (!level.isLoaded(supportPos)) continue;
            var shape = level.getBlockState(supportPos).getCollisionShape(level, supportPos);
            if (shape.isEmpty()) continue;
            double standY = y + shape.max(Direction.Axis.Y);
            if (standY > currentY + 1.05D || standY < currentY - 1.25D) continue;
            double distance = Math.abs(standY - preferredY);
            if (distance < selectedDistance) {
                selectedY = standY;
                selectedDistance = distance;
            }
        }
        return selectedY;
    }

    // Get horizontal distance without treating a supported step height as a separate destination
    private static double horizontalDistanceSqr(Vec3 first, Vec3 second) {
        if (first == null || second == null) return Double.MAX_VALUE;
        double x = first.x - second.x;
        double z = first.z - second.z;
        return x * x + z * z;
    }

    // Check whether one ground cell supports a standing worker
    private static boolean canStandAt(Level level, BlockPos pos) {
        if (!level.isLoaded(pos) || !level.isLoaded(pos.above()) || !level.isLoaded(pos.below())) return false;
        if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return false;
        if (!level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) return false;
        return !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
    }

    // Validate a jump arc for one supported one-block ground rise without permitting a collision shortcut.
    private static boolean isOneBlockStep(SablePathfinder.Query query, SablePathfinder.Validator collision) {
        if (query == null || query.rootLevel() == null) return false;
        Vec3 start = query.start();
        Vec3 end = query.end();
        double rise = end.y - start.y;
        if (rise <= 0.05D || rise > 1.05D || horizontalDistanceSqr(start, end) > 2.1D) return false;
        Vec3 apex = new Vec3(start.x, end.y + 0.05D, start.z);
        SablePathfinder.Query lift = new SablePathfinder.Query(query.rootLevel(), start, apex,
                query.safety(), SablePathfinder.RouteMode.FLIGHT);
        SablePathfinder.Query forward = new SablePathfinder.Query(query.rootLevel(), apex, end,
                query.safety(), SablePathfinder.RouteMode.FLIGHT);
        return collision.validate(lift).result() == SablePathfinder.TraversalResult.CLEAR
                && collision.validate(forward).result() == SablePathfinder.TraversalResult.CLEAR;
    }

    // Advance one clear airborne leg at the requested speed.
    private static Vec3 advanceFlight(Vec3 current, Vec3 target, double maximumDistance) {
        Vec3 start = current == null ? Vec3.ZERO : current;
        Vec3 end = target == null ? start : target;
        double distance = start.distanceTo(end);
        if (distance <= 1.0E-9D || distance <= maximumDistance) return end;
        return start.add(end.subtract(start).scale(Math.max(0.0D, maximumDistance) / distance));
    }

    // Get the appropriate completion distance for a grounded or airborne path leg.
    private static double arrivalDistanceSqr(Vec3 first, Vec3 second, boolean vertical) {
        return vertical ? first.distanceToSqr(second) : horizontalDistanceSqr(first, second);
    }

    // Order the preferred SCM face before every remaining horizontal exterior face
    private static List<Direction> interactionFaces(@Nullable Direction preferredFace) {
        List<Direction> faces = new ArrayList<>();
        if (preferredFace != null && preferredFace.getAxis().isHorizontal()) faces.add(preferredFace);
        for (Direction face : Direction.Plane.HORIZONTAL) {
            if (face != preferredFace) faces.add(face);
        }
        return faces;
    }
}
