package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.AllTags;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.navigation.ReactiveCollisionAvoidance;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.physics.collider.SableCollisionContext;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

// Trace particles and ship clearance across the root world and every loaded Sable sub-level
public final class SubLevelParticleOcclusion {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final double EPSILON = 1.0E-7D;
    private static final double COLLISION_MARGIN = 0.015625D;
    // Keep each probe pass bounded with a fixed cache
    private static final int PROBE_SHAPE_CACHE_CAPACITY = 4096;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the sub level particle occlusion
    private SubLevelParticleOcclusion() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Inspect authoritative live poses before an intruder enters the requested travel corridor
    public static ReactiveCollisionAvoidance.ProximityHazard findSubLevelProximityHazard(Level level,
            List<AABB> movingBounds, Vec3 velocity, Set<UUID> excludedIds, double clearance, double responseSeconds){
        return findSubLevelProximityHazard(level, movingBounds, velocity, excludedIds, clearance, responseSeconds, null);
    }

    // Share one authoritative body list with the controller's directional queries
    public static ReactiveCollisionAvoidance.ProximityHazard findSubLevelProximityHazard(Level level,
            List<AABB> movingBounds, Vec3 velocity, Set<UUID> excludedIds, double clearance, double responseSeconds,
            @Nullable ProbeCache cache){
        List<ReactiveCollisionAvoidance.MovingObstacle> obstacles = new ArrayList<>();
        for(Object candidate : knownSubLevels(level, cache)){
            if(!(candidate instanceof SubLevel body) || body.isRemoved() || excludedIds.contains(body.getUniqueId())) continue;
            Vec3 motion = body instanceof ServerSubLevel server
                    ? new Vec3(server.latestLinearVelocity.x, server.latestLinearVelocity.y, server.latestLinearVelocity.z) : Vec3.ZERO;
            AABB bounds = subLevelWorldBounds(body);
            if(bounds != null) obstacles.add(new ReactiveCollisionAvoidance.MovingObstacle(bounds, motion));
        }
        return ReactiveCollisionAvoidance.proximityHazard(movingBounds, velocity, obstacles, clearance, responseSeconds);
    }

    // Recover from forced physical overlap while retaining terrain and new-obstacle checks
    public static double findAirOnlyDepartureBlockingDistance(Level level, Vec3 direction, double range,
            List<AABB> movingBounds, boolean includeWorld, Set<UUID> excludedIds, int probes,
            @Nullable ProbeCache cache, double padding){
        double distance = findAirOnlyBoundsBlockingDistance(level, direction, range, movingBounds,
                includeWorld, excludedIds, probes, cache, padding);
        if(distance > EPSILON) return distance;
        List<AABB> physical = movingBounds.stream().map(bounds -> contactBounds(bounds, padding))
                .filter(java.util.Objects::nonNull).toList();
        Set<UUID> exclusions = new HashSet<>(excludedIds);
        for(Object candidate : knownSubLevels(level, cache)){
            if(!(candidate instanceof SubLevel body) || body.isRemoved() || exclusions.contains(body.getUniqueId())) continue;
            AABB bounds = subLevelWorldBounds(body);
            if(bounds != null && allowsOverlapDeparture(physical, bounds, direction, range))
                exclusions.add(body.getUniqueId());
        }
        return exclusions.equals(excludedIds) && !includeWorld ? distance
                : findAirOnlyBoundsBlockingDistance(level, direction, range, movingBounds,
                        includeWorld, exclusions, probes, cache, padding, physical);
    }

    // Permit only outward translation for every overlapping piece of a compound hull
    public static boolean allowsOverlapDeparture(List<AABB> bounds, AABB obstacle, Vec3 direction, double range){
        boolean overlapping = false;
        for(AABB hull : bounds){
            if(!hull.intersects(obstacle)){
                if(translatedBoundsEntryDistance(hull, obstacle, direction, range) != null) return false;
                continue;
            }
            overlapping = true;
            boolean outward = direction.x < -EPSILON && hull.maxX - obstacle.minX <= obstacle.maxX - hull.minX + EPSILON
                    || direction.x > EPSILON && obstacle.maxX - hull.minX <= hull.maxX - obstacle.minX + EPSILON
                    || direction.y < -EPSILON && hull.maxY - obstacle.minY <= obstacle.maxY - hull.minY + EPSILON
                    || direction.y > EPSILON && obstacle.maxY - hull.minY <= hull.maxY - obstacle.minY + EPSILON
                    || direction.z < -EPSILON && hull.maxZ - obstacle.minZ <= obstacle.maxZ - hull.minZ + EPSILON
                    || direction.z > EPSILON && obstacle.maxZ - hull.minZ <= hull.maxZ - obstacle.minZ + EPSILON;
            if(!outward) return false;
        }
        return overlapping;
    }

    // Find the blocking distance
    public static double findBlockingDistance(Level rootLevel, Vec3 startWorld, Vec3 directionWorld,
            double maxDistance, boolean includeRootLevel) {
        return findBlockingDistance(
                rootLevel, null, startWorld, directionWorld, maxDistance, includeRootLevel, Set.of());
    }

    // Find the blocking distance
    public static double findBlockingDistance(Level rootLevel, @Nullable Object containingSubLevel,
            Vec3 startWorld, Vec3 directionWorld, double maxDistance, boolean includeRootLevel) {
        return findBlockingDistance(
                rootLevel, containingSubLevel, startWorld, directionWorld,
                maxDistance, includeRootLevel, Set.of());
    }

    // Find the blocking distance
    public static double findBlockingDistance(Level rootLevel, @Nullable Object containingSubLevel,
            Vec3 startWorld, Vec3 directionWorld, double maxDistance, boolean includeRootLevel,
            Set<UUID> excludedSubLevelIds) {
        return findBlockingDistance(
                rootLevel, containingSubLevel, startWorld, directionWorld, maxDistance,
                includeRootLevel, excludedSubLevelIds, false);
    }

    // Find the blocking distance
    public static double findBlockingDistance(Level rootLevel, @Nullable Object containingSubLevel,
            Vec3 startWorld, Vec3 directionWorld, double maxDistance, boolean includeRootLevel,
            Set<UUID> excludedSubLevelIds, boolean includeTaggedTransparentBlocks) {
        return findBlockingDistance(rootLevel, containingSubLevel, startWorld, directionWorld,
                maxDistance, includeRootLevel, excludedSubLevelIds, includeTaggedTransparentBlocks, null);
    }

    // Reuse loaded shapes and SubLevels across nearby traces in the same game tick
    public static double findBlockingDistance(Level rootLevel, @Nullable Object containingSubLevel,
            Vec3 startWorld, Vec3 directionWorld, double maxDistance, boolean includeRootLevel,
            Set<UUID> excludedSubLevelIds, boolean includeTaggedTransparentBlocks,
            @Nullable ProbeCache probeCache) {
        return findBlockingDistance(rootLevel, containingSubLevel, startWorld, directionWorld,
                maxDistance, includeRootLevel, excludedSubLevelIds, includeTaggedTransparentBlocks,
                probeCache, null);
    }

    // Trace only block states that can hide a particle from the camera
    public static double findVisualBlockingDistance(Level rootLevel, Vec3 startWorld,
            Vec3 directionWorld, double maxDistance, Predicate<BlockState> blocksView,
            @Nullable ProbeCache probeCache) {
        return findBlockingDistance(rootLevel, null, startWorld, directionWorld,
                maxDistance, true, Set.of(), false, probeCache, blocksView);
    }

    // Apply an optional visual filter without changing physical collision queries
    private static double findBlockingDistance(Level rootLevel, @Nullable Object containingSubLevel,
            Vec3 startWorld, Vec3 directionWorld, double maxDistance, boolean includeRootLevel,
            Set<UUID> excludedSubLevelIds, boolean includeTaggedTransparentBlocks,
            @Nullable ProbeCache probeCache, @Nullable Predicate<BlockState> blocksView) {
        if (rootLevel == null || startWorld == null || directionWorld == null
                || directionWorld.lengthSqr() < EPSILON || maxDistance <= 0.0D) {
            return Math.max(0.0D, maxDistance);
        }
        Vec3 worldDirection = directionWorld.normalize();
        Vec3 endWorld = startWorld.add(worldDirection.scale(maxDistance));
        AABB worldBounds = new AABB(startWorld, endWorld).inflate(1.0D);
        List<Object> subLevels = intersectingSubLevels(rootLevel, worldBounds, probeCache, excludedSubLevelIds);
        if (containingSubLevel != null && !containsIdentity(subLevels, containingSubLevel)) {
            subLevels.add(0, containingSubLevel);
        }
        return findBlockingDistanceAcrossLevels(
                rootLevel, startWorld, endWorld, worldDirection, maxDistance,
                includeRootLevel, excludedSubLevelIds,
                includeTaggedTransparentBlocks, subLevels,
                probeCache == null ? null : probeCache.lookups, blocksView);
    }

    // Find the blocking distance across levels
    private static double findBlockingDistanceAcrossLevels(
            Level rootLevel,
            Vec3 startWorld,
            Vec3 endWorld,
            Vec3 worldDirection,
            double maxDistance,
            boolean includeRootLevel,
            Set<UUID> excludedSubLevelIds,
            boolean includeTaggedTransparentBlocks,
            List<Object> subLevels,
            @Nullable Map<BlockGetter, LoadedBlockLookup> sharedLookups
    ) {
        return findBlockingDistanceAcrossLevels(rootLevel, startWorld, endWorld, worldDirection,
                maxDistance, includeRootLevel, excludedSubLevelIds, includeTaggedTransparentBlocks,
                subLevels, sharedLookups, 0.0D, null);
    }

    // Check view blocking with the caller's block-state filter
    private static double findBlockingDistanceAcrossLevels(
            Level rootLevel, Vec3 startWorld, Vec3 endWorld, Vec3 worldDirection,
            double maxDistance, boolean includeRootLevel, Set<UUID> excludedSubLevelIds,
            boolean includeTaggedTransparentBlocks, List<Object> subLevels,
            @Nullable Map<BlockGetter, LoadedBlockLookup> sharedLookups,
            @Nullable Predicate<BlockState> blocksView
    ) {
        return findBlockingDistanceAcrossLevels(rootLevel, startWorld, endWorld, worldDirection,
                maxDistance, includeRootLevel, excludedSubLevelIds, includeTaggedTransparentBlocks,
                subLevels, sharedLookups, 0.0D, blocksView);
    }

    // Resting contact may be ignored only when the requested movement leaves it
    private static double findBlockingDistanceAcrossLevels(
            Level rootLevel, Vec3 startWorld, Vec3 endWorld, Vec3 worldDirection,
            double maxDistance, boolean includeRootLevel, Set<UUID> excludedSubLevelIds,
            boolean includeTaggedTransparentBlocks, List<Object> subLevels,
            @Nullable Map<BlockGetter, LoadedBlockLookup> sharedLookups, double initialContactAllowance
    ){
        return findBlockingDistanceAcrossLevels(rootLevel, startWorld, endWorld, worldDirection,
                maxDistance, includeRootLevel, excludedSubLevelIds, includeTaggedTransparentBlocks,
                subLevels, sharedLookups, initialContactAllowance, null);
    }

    // Resting contact may be ignored only when the requested movement leaves it
    private static double findBlockingDistanceAcrossLevels(
            Level rootLevel, Vec3 startWorld, Vec3 endWorld, Vec3 worldDirection,
            double maxDistance, boolean includeRootLevel, Set<UUID> excludedSubLevelIds,
            boolean includeTaggedTransparentBlocks, List<Object> subLevels,
            @Nullable Map<BlockGetter, LoadedBlockLookup> sharedLookups, double initialContactAllowance,
            @Nullable Predicate<BlockState> blocksView
    ){
        if (!includeRootLevel && subLevels.isEmpty()) {
            return maxDistance;
        }

        Double nearest = includeRootLevel
                ? findBlockingDistanceInLevel(rootLevel, null, startWorld, endWorld,
                        startWorld, worldDirection, maxDistance, includeTaggedTransparentBlocks,
                        loadedBlockLookup(rootLevel, sharedLookups), initialContactAllowance, blocksView)
                : null;

        for (Object subLevel : subLevels) {
            UUID subLevelId = subLevelId(subLevel);
            if (subLevelId != null && excludedSubLevelIds != null
                    && excludedSubLevelIds.contains(subLevelId)) {
                continue;
            }
            BlockGetter subLevelLevel = getSubLevelBlockGetter(subLevel);
            if (subLevelLevel == null) {
                continue;
            }

            Vec3 localStart = toSubLevelPosition(subLevel, startWorld);
            Vec3 localEnd = toSubLevelPosition(subLevel, endWorld);
            if (localStart == null || localEnd == null) {
                continue;
            }

            Double subLevelDistance = findBlockingDistanceInLevel(subLevelLevel, subLevel, localStart, localEnd,
                    startWorld, worldDirection, maxDistance, includeTaggedTransparentBlocks,
                    loadedBlockLookup(subLevelLevel, sharedLookups), initialContactAllowance, blocksView);
            nearest = nearestDistance(nearest, subLevelDistance);
        }

        return nearest == null ? maxDistance : Mth.clamp(nearest - 0.0625D, 0.0D, maxDistance);
    }

    // Find the probed bounds blocking distance
    public static double findProbedBoundsBlockingDistance(
            Level rootLevel,
            @Nullable Object containingSubLevel,
            Vec3 directionWorld,
            double maxDistance,
            List<AABB> movingBoundsWorld,
            boolean includeRootLevel,
            Set<UUID> excludedSubLevelIds,
            boolean includeTaggedTransparentBlocks,
            int maximumProbesPerBounds
    ) {
        return findProbedBoundsBlockingDistance(
                rootLevel, containingSubLevel, directionWorld, maxDistance,
                movingBoundsWorld, includeRootLevel, excludedSubLevelIds,
                includeTaggedTransparentBlocks, maximumProbesPerBounds, null);
    }

    // Find the probed bounds blocking distance
    public static double findProbedBoundsBlockingDistance(
            Level rootLevel,
            @Nullable Object containingSubLevel,
            Vec3 directionWorld,
            double maxDistance,
            List<AABB> movingBoundsWorld,
            boolean includeRootLevel,
            Set<UUID> excludedSubLevelIds,
            boolean includeTaggedTransparentBlocks,
            int maximumProbesPerBounds,
            @Nullable ProbeCache probeCache
    ) {
        return findProbedBoundsBlockingDistance(rootLevel, containingSubLevel, directionWorld,
                maxDistance, movingBoundsWorld, includeRootLevel, excludedSubLevelIds,
                includeTaggedTransparentBlocks, maximumProbesPerBounds, probeCache, 0.0D);
    }

    // Contact-aware probes allow takeoff without changing ordinary pathfinder queries
    public static double findProbedBoundsBlockingDistance(
            Level rootLevel, @Nullable Object containingSubLevel, Vec3 directionWorld,
            double maxDistance, List<AABB> movingBoundsWorld, boolean includeRootLevel,
            Set<UUID> excludedSubLevelIds, boolean includeTaggedTransparentBlocks,
            int maximumProbesPerBounds, @Nullable ProbeCache probeCache, double initialContactAllowance
    ){
        if (rootLevel == null || directionWorld == null
                || directionWorld.lengthSqr() < EPSILON || maxDistance <= 0.0D
                || movingBoundsWorld == null || movingBoundsWorld.isEmpty()) {
            return Math.max(0.0D, maxDistance);
        }
        Vec3 dir = directionWorld.normalize();
        Vec3 maximumDelta = dir.scale(maxDistance);
        AABB queryBounds = null;
        for (AABB bounds : movingBoundsWorld) {
            if (bounds == null) {
                continue;
            }
            AABB swept = sweptBounds(bounds, maximumDelta).inflate(1.0D);
            queryBounds = queryBounds == null ? swept : queryBounds.minmax(swept);
        }
        if (queryBounds == null) {
            return maxDistance;
        }
        List<Object> subLevels = intersectingSubLevels(
                rootLevel, queryBounds, probeCache, excludedSubLevelIds);
        if (containingSubLevel != null
                && !containsIdentity(subLevels, containingSubLevel)) {
            subLevels.add(0, containingSubLevel);
        }
        Map<BlockGetter, LoadedBlockLookup> sharedLookups = probeCache == null
                ? new IdentityHashMap<>() : probeCache.lookups;
        // Leading-face probes refine root and SubLevel collision shapes directly. Expanding every
        // broad-phase SubLevel envelope into a complete swept-volume scan here multiplies work by
        // every hull bound, direction and telemetry consumer. Immediate moving-body avoidance uses
        // findSubLevelEnvelopeBlockingDistance separately and therefore remains conservative.
        double nearest = maxDistance;
        Vec3 probeOffset = dir.scale(COLLISION_MARGIN * 2.0D);
        for (AABB bounds : movingBoundsWorld) {
            if (bounds == null) {
                continue;
            }
            for (Vec3 start : leadingFaceProbePoints(
                    bounds, dir, maximumProbesPerBounds)) {
                Vec3 probeStart = start.add(probeOffset);
                Vec3 probeEnd = probeStart.add(dir.scale(nearest));
                double distance = findBlockingDistanceAcrossLevels(
                        rootLevel, probeStart, probeEnd, dir, nearest,
                        includeRootLevel, excludedSubLevelIds,
                        includeTaggedTransparentBlocks, subLevels, sharedLookups,
                        Math.max(0.0D, initialContactAllowance));
                nearest = Math.min(nearest, distance);
                if (nearest <= EPSILON) {
                    return 0.0D;
                }
            }
        }
        return Mth.clamp(nearest, 0.0D, maxDistance);
    }

    // Find compound-hull clearance where only loaded root-level air is traversable
    public static double findAirOnlyBoundsBlockingDistance(
            Level rootLevel,
            Vec3 directionWorld,
            double maxDistance,
            List<AABB> movingBoundsWorld,
            boolean includeRootLevel,
            Set<UUID> excludedSubLevelIds,
            int maximumProbesPerBounds,
            @Nullable ProbeCache probeCache
    ) {
        return findAirOnlyBoundsBlockingDistance(rootLevel, directionWorld, maxDistance,
                movingBoundsWorld, includeRootLevel, excludedSubLevelIds,
                maximumProbesPerBounds, probeCache, 0.0D);
    }

    // Allow a padded hull to leave initial contact while keeping physical bodies separated
    public static double findAirOnlyBoundsBlockingDistance(
            Level rootLevel, Vec3 directionWorld, double maxDistance,
            List<AABB> movingBoundsWorld, boolean includeRootLevel,
            Set<UUID> excludedSubLevelIds, int maximumProbesPerBounds,
            @Nullable ProbeCache probeCache, double initialContactAllowance
    ){
        return findAirOnlyBoundsBlockingDistance(rootLevel, directionWorld, maxDistance,
                movingBoundsWorld, includeRootLevel, excludedSubLevelIds, maximumProbesPerBounds,
                probeCache, initialContactAllowance, List.of());
    }

    private static double findAirOnlyBoundsBlockingDistance(
            Level rootLevel, Vec3 directionWorld, double maxDistance,
            List<AABB> movingBoundsWorld, boolean includeRootLevel,
            Set<UUID> excludedSubLevelIds, int maximumProbesPerBounds,
            @Nullable ProbeCache probeCache, double initialContactAllowance, List<AABB> departureBounds
    ){
        if (rootLevel == null || directionWorld == null
                || directionWorld.lengthSqr() < EPSILON || maxDistance <= 0.0D
                || movingBoundsWorld == null || movingBoundsWorld.isEmpty()) {
            return Math.max(0.0D, maxDistance);
        }
        Vec3 direction = directionWorld.normalize();
        double nearest = findSubLevelEnvelopeBlockingDistance(rootLevel, direction, maxDistance, movingBoundsWorld,
                excludedSubLevelIds, probeCache, initialContactAllowance);
        if(nearest <= EPSILON) return 0.0D;
        Vec3 probeOffset = direction.scale(COLLISION_MARGIN * 2.0D);
        Map<BlockGetter, LoadedBlockLookup> sharedLookups = probeCache == null
                ? new IdentityHashMap<>() : probeCache.lookups;
        if (includeRootLevel) {
            LoadedBlockLookup lookup = loadedBlockLookup(rootLevel, sharedLookups);
            for (AABB bounds : movingBoundsWorld) {
                if (bounds == null) continue;
                for (Vec3 start : leadingFaceProbePoints(
                        bounds, direction, maximumProbesPerBounds)) {
                    Vec3 probeStart = start.add(probeOffset);
                    double distance = findAirOnlyBlockingDistanceInLevel(
                            lookup, probeStart, direction, nearest, bounds,
                            contactBounds(bounds, initialContactAllowance), departureBounds);
                    nearest = Math.min(nearest, distance);
                    if (nearest <= EPSILON) return 0.0D;
                }
            }
        }
        return nearest;
    }

    /**
     * Find the first broad-phase overlap distance between translated moving
     * bounds and fixed obstacle envelopes. An envelope is not a collision
     * shape; callers requiring physical clearance must refine a candidate
     * against block collision shapes.
     */
    public static double findEnvelopeBlockingDistance(
            Vec3 directionWorld,
            double maxDistance,
            List<AABB> movingBoundsWorld,
            List<AABB> obstacleBoundsWorld
    ) {
        return findEnvelopeBlockingDistance(directionWorld, maxDistance,
                movingBoundsWorld, obstacleBoundsWorld, 0.0D);
    }

    // Preserve clearance for parallel or separating motion within existing hull padding
    public static double findEnvelopeBlockingDistance(
            Vec3 directionWorld, double maxDistance, List<AABB> movingBoundsWorld,
            List<AABB> obstacleBoundsWorld, double initialContactAllowance
    ){
        double maximum = Double.isFinite(maxDistance)
                ? Math.max(0.0D, maxDistance) : 0.0D;
        if (directionWorld == null || directionWorld.lengthSqr() < EPSILON
                || maximum <= 0.0D || movingBoundsWorld == null
                || movingBoundsWorld.isEmpty() || obstacleBoundsWorld == null
                || obstacleBoundsWorld.isEmpty()) {
            return maximum;
        }
        Vec3 direction = directionWorld.normalize();
        double nearest = maximum;
        for (AABB moving : movingBoundsWorld) {
            if (moving == null) continue;
            for (AABB obstacle : obstacleBoundsWorld) {
                if (obstacle == null) continue;
                if(leavesPaddedContact(moving, contactBounds(moving, initialContactAllowance),
                        obstacle, direction)) continue;
                Double distance = translatedBoundsEntryDistance(
                        moving, obstacle, direction, nearest);
                if (distance == null) continue;
                nearest = Math.min(nearest, distance);
                if (nearest <= EPSILON) return 0.0D;
            }
        }
        return Mth.clamp(nearest, 0.0D, maximum);
    }

    /**
     * Find the first broad-phase collision distance between a translated hull
     * and every live, non-excluded Sable body in the root-level container.
     *
     * <p>This is intended for immediate moving-body safety overrides. Route
     * planning should continue to use the collision-shape-refined queries so
     * sparse SubLevel envelopes do not become permanent route obstacles.</p>
     */
    public static double findSubLevelEnvelopeBlockingDistance(
            Level rootLevel,
            Vec3 directionWorld,
            double maxDistance,
            List<AABB> movingBoundsWorld,
            Set<UUID> excludedSubLevelIds,
            @Nullable ProbeCache probeCache
    ) {
        return findSubLevelEnvelopeBlockingDistance(rootLevel, directionWorld, maxDistance,
                movingBoundsWorld, excludedSubLevelIds, probeCache, 0.0D);
    }

    // Keep neighbouring ships blocking inward motion while permitting a safe escape
    public static double findSubLevelEnvelopeBlockingDistance(
            Level rootLevel, Vec3 directionWorld, double maxDistance,
            List<AABB> movingBoundsWorld, Set<UUID> excludedSubLevelIds,
            @Nullable ProbeCache probeCache, double initialContactAllowance
    ){
        if (rootLevel == null || directionWorld == null
                || directionWorld.lengthSqr() < EPSILON || maxDistance <= 0.0D
                || movingBoundsWorld == null || movingBoundsWorld.isEmpty()) {
            return Math.max(0.0D, maxDistance);
        }
        Vec3 direction = directionWorld.normalize();
        Vec3 maximumDelta = direction.scale(maxDistance);
        AABB queryBounds = null;
        for (AABB bounds : movingBoundsWorld) {
            if (bounds == null) continue;
            AABB swept = sweptBounds(bounds, maximumDelta).inflate(1.0D);
            queryBounds = queryBounds == null ? swept : queryBounds.minmax(swept);
        }
        if (queryBounds == null) return maxDistance;
        List<Object> subLevels = intersectingSubLevels(
                rootLevel, queryBounds, probeCache, excludedSubLevelIds);
        return findEnvelopeBlockingDistance(
                direction, maxDistance, movingBoundsWorld,
                subLevelWorldBounds(subLevels, excludedSubLevelIds), initialContactAllowance);
    }

    // Handle the probe cache
    public static final class ProbeCache {
        // Tracked lookups
        private final Map<BlockGetter, LoadedBlockLookup> lookups =
                new IdentityHashMap<>();
        // Current root-level Sable bodies
        private @Nullable Level subLevelRoot;
        // Current root-level Sable body snapshot
        private List<Object> subLevels = List.of();
        private int subLevelCount = -1;

        // Refresh additions and same-size replacements without retaining stale body identities
        private boolean currentBodies(Level level){
            if(subLevelRoot != level || subLevelCount != bodyCount(level)) return false;
            for(Object body : subLevels) if(body instanceof SubLevel subLevel && subLevel.isRemoved()) return false;
            return true;
        }

        // Clear the probe cache
        public void clear() {
            lookups.clear();
            subLevelRoot = null;
            subLevels = List.of();
            subLevelCount = -1;
        }
    }

    // Remove the shared support-contact band from a compound ground hull.
    // Ground vehicles may rest on a block or another supported surface. That
    // contact is not forward obstruction, so callers should remove it before
    // asking a horizontal clearance question. The returned bounds continue to
    // cover every part of the hull above the band, including low obstacles
    // which project into the vehicle body.
    public static List<AABB> withoutGroundContactBand(
            List<AABB> movingBoundsWorld,
            double contactClearance
    ) {
        if (movingBoundsWorld == null || movingBoundsWorld.isEmpty()) {
            return List.of();
        }
        double supportY = Double.POSITIVE_INFINITY;
        List<AABB> validBounds = new ArrayList<>(movingBoundsWorld.size());
        for (AABB bounds : movingBoundsWorld) {
            if (bounds == null) {
                continue;
            }
            validBounds.add(bounds);
            supportY = Math.min(supportY, bounds.minY);
        }
        if (validBounds.isEmpty() || !Double.isFinite(supportY)) {
            return List.of();
        }
        double clearance = Math.max(0.0D, contactClearance);
        double collisionFloor = supportY + clearance;
        List<AABB> clipped = new ArrayList<>(validBounds.size());
        for (AABB bounds : validBounds) {
            double minimumY = Math.max(bounds.minY, collisionFloor);
            if (bounds.maxY - minimumY > EPSILON) {
                clipped.add(new AABB(bounds.minX, minimumY, bounds.minZ,
                        bounds.maxX, bounds.maxY, bounds.maxZ));
            }
        }
        // A very thin actor has no meaningful body above the requested band.
        // Preserve its original bounds rather than accidentally making it
        // collision-free.
        return List.copyOf(clipped.isEmpty() ? validBounds : clipped);
    }

    // Expand horizontal clearance without probing support or overhead contact for each body
    public static List<AABB> sideClearanceBounds(List<AABB> bounds, double margin, double contactClearance){
        if(bounds == null || bounds.isEmpty()) return List.of();
        double padding = Math.max(0.0D, margin);
        double contact = Math.max(EPSILON * 2.0D, contactClearance);
        List<AABB> res = new ArrayList<>();
        for(AABB box : bounds){
            if(box == null) continue;
            double inset = Math.min(contact, Math.max(0.0D, box.getYsize()) * 0.25D);
            res.add(new AABB(box.minX - padding, box.minY + inset, box.minZ - padding,
                    box.maxX + padding, box.maxY - inset, box.maxZ + padding));
        }
        return List.copyOf(res);
    }

    // Get the leading face probe points
    static List<Vec3> leadingFaceProbePoints(
            AABB bounds,
            Vec3 dir,
            int maximumProbes
    ) {
        if (bounds == null || dir == null || dir.lengthSqr() < EPSILON) {
            return List.of();
        }
        int faceCount = (Math.abs(dir.x) > EPSILON ? 1 : 0)
                + (Math.abs(dir.y) > EPSILON ? 1 : 0)
                + (Math.abs(dir.z) > EPSILON ? 1 : 0);
        int perFace = Math.max(4, Math.max(1, maximumProbes) / Math.max(1, faceCount));
        int resolution = Math.max(2, (int) Math.floor(Math.sqrt(perFace)));
        List<Vec3> probes = new ArrayList<>(resolution * resolution * faceCount);
        if (Math.abs(dir.x) > EPSILON) {
            addFaceGrid(probes, resolution,
                    dir.x > 0.0D ? bounds.maxX : bounds.minX,
                    bounds.minY, bounds.maxY, bounds.minZ, bounds.maxZ, 0);
        }
        if (Math.abs(dir.y) > EPSILON) {
            addFaceGrid(probes, resolution,
                    dir.y > 0.0D ? bounds.maxY : bounds.minY,
                    bounds.minX, bounds.maxX, bounds.minZ, bounds.maxZ, 1);
        }
        if (Math.abs(dir.z) > EPSILON) {
            addFaceGrid(probes, resolution,
                    dir.z > 0.0D ? bounds.maxZ : bounds.minZ,
                    bounds.minX, bounds.maxX, bounds.minY, bounds.maxY, 2);
        }
        if (probes.isEmpty()) {
            probes.add(bounds.getCenter());
        }
        return List.copyOf(probes);
    }

    // Add the face grid
    private static void addFaceGrid(
            List<Vec3> probes,
            int resolution,
            double fixed,
            double firstMinimum,
            double firstMaximum,
            double secondMinimum,
            double secondMaximum,
            int fixedAxis
    ) {
        for (int first = 0; first < resolution; first++) {
            double firstValue = Mth.lerp(
                    resolution == 1 ? 0.5D : (double) first / (resolution - 1),
                    firstMinimum, firstMaximum);
            for (int second = 0; second < resolution; second++) {
                double secondValue = Mth.lerp(
                        resolution == 1 ? 0.5D : (double) second / (resolution - 1),
                        secondMinimum, secondMaximum);
                probes.add(switch (fixedAxis) {
                    case 0 -> new Vec3(fixed, firstValue, secondValue);
                    case 1 -> new Vec3(firstValue, fixed, secondValue);
                    default -> new Vec3(firstValue, secondValue, fixed);
                });
            }
        }
    }

    // Return no movement authority until a bounded exact sweep is complete
    public static double findSweptBoundsBlockingDistance(
            Level rootLevel, @Nullable Object containingSubLevel,
            Vec3 anchorStartWorld, Vec3 directionWorld, double maxDistance,
            List<AABB> movingBoundsWorld, boolean includeRootLevel,
            Set<UUID> excludedSubLevelIds, boolean includeTaggedTransparentBlocks,
            int maximumBlocks, long maximumNanos
    ){
        if(maximumBlocks <= 0 || maximumNanos <= 0L) return 0.0D;
        SweptBoundsScan scan = beginSweptBoundsBlockingDistanceScan(rootLevel, containingSubLevel,
                anchorStartWorld, directionWorld, maxDistance, movingBoundsWorld,
                includeRootLevel, excludedSubLevelIds, includeTaggedTransparentBlocks);
        return scan.advance(maximumBlocks, maximumNanos) ? scan.result() : 0.0D;
    }

    // Find the swept bounds blocking distance
    public static double findSweptBoundsBlockingDistance(
            Level rootLevel,
            @Nullable Object containingSubLevel,
            Vec3 anchorStartWorld,
            Vec3 directionWorld,
            double maxDistance,
            List<AABB> movingBoundsWorld,
            boolean includeRootLevel,
            Set<UUID> excludedSubLevelIds,
            boolean includeTaggedTransparentBlocks
    ) {
        if (rootLevel == null || anchorStartWorld == null || directionWorld == null
                || directionWorld.lengthSqr() < EPSILON || maxDistance <= 0.0D
                || movingBoundsWorld == null || movingBoundsWorld.isEmpty()) {
            return Math.max(0.0D, maxDistance);
        }

        Vec3 worldDirection = directionWorld.normalize();
        Vec3 worldDelta = worldDirection.scale(maxDistance);
        Vec3 anchorEndWorld = anchorStartWorld.add(worldDelta);
        AABB sweptWorldBounds = null;
        for (AABB bounds : movingBoundsWorld) {
            if (bounds == null) {
                continue;
            }
            AABB swept = sweptBounds(bounds, worldDelta).inflate(1.0D);
            sweptWorldBounds = sweptWorldBounds == null
                    ? swept : sweptWorldBounds.minmax(swept);
        }
        if (sweptWorldBounds == null) {
            return maxDistance;
        }

        List<Object> subLevels = intersectingSubLevels(
                rootLevel, sweptWorldBounds, null, excludedSubLevelIds);
        if (containingSubLevel != null && !containsIdentity(subLevels, containingSubLevel)) {
            subLevels.add(0, containingSubLevel);
        }

        Double nearest = null;
        if (includeRootLevel) {
            for (AABB bounds : movingBoundsWorld) {
                if (bounds == null) {
                    continue;
                }
                nearest = nearestDistance(nearest, findSweptBoundsDistanceInLevel(
                        rootLevel, anchorStartWorld, anchorEndWorld, bounds,
                        includeTaggedTransparentBlocks));
                if (isImmediateHit(nearest, maxDistance)) {
                    return 0.0D;
                }
            }
        }

        for (Object subLevel : subLevels) {
            UUID subLevelId = subLevelId(subLevel);
            if (subLevelId != null && excludedSubLevelIds != null
                    && excludedSubLevelIds.contains(subLevelId)) {
                continue;
            }
            BlockGetter subLevelLevel = getSubLevelBlockGetter(subLevel);
            if (subLevelLevel == null) {
                continue;
            }
            Vec3 localStart = toSubLevelPosition(subLevel, anchorStartWorld);
            Vec3 localEnd = toSubLevelPosition(subLevel, anchorEndWorld);
            if (localStart == null || localEnd == null) {
                continue;
            }
            for (AABB worldBounds : movingBoundsWorld) {
                AABB localBounds = transformWorldBoundsToLocal(subLevel, worldBounds);
                if (localBounds == null) {
                    continue;
                }
                nearest = nearestDistance(nearest, findSweptBoundsDistanceInLevel(
                        subLevelLevel, localStart, localEnd, localBounds,
                        includeTaggedTransparentBlocks));
                if (isImmediateHit(nearest, maxDistance)) {
                    return 0.0D;
                }
            }
        }

        return nearest == null
                ? maxDistance
                : Mth.clamp(nearest * maxDistance - 0.0625D, 0.0D, maxDistance);
    }

    // Sweep a padded hull without rejecting safe motion away from initial contact
    public static double findSweptBoundsBlockingDistance(
            Level rootLevel, @Nullable Object containingSubLevel,
            Vec3 anchorStartWorld, Vec3 directionWorld, double maxDistance,
            List<AABB> movingBoundsWorld, boolean includeRootLevel,
            Set<UUID> excludedSubLevelIds, boolean includeTaggedTransparentBlocks,
            double initialContactAllowance
    ){
        SweptBoundsScan scan = beginSweptBoundsBlockingDistanceScan(rootLevel, containingSubLevel,
                anchorStartWorld, directionWorld, maxDistance, movingBoundsWorld,
                includeRootLevel, excludedSubLevelIds, includeTaggedTransparentBlocks,
                initialContactAllowance);
        while(!scan.advance(Integer.MAX_VALUE, 0L)){}
        return scan.result();
    }

    // Begin the swept bounds blocking distance scan
    public static SweptBoundsScan beginSweptBoundsBlockingDistanceScan(
            Level rootLevel,
            @Nullable Object containingSubLevel,
            Vec3 anchorStartWorld,
            Vec3 directionWorld,
            double maxDistance,
            List<AABB> movingBoundsWorld,
            boolean includeRootLevel,
            Set<UUID> excludedSubLevelIds,
            boolean includeTaggedTransparentBlocks
    ) {
        return beginSweptBoundsBlockingDistanceScan(rootLevel, containingSubLevel,
                anchorStartWorld, directionWorld, maxDistance, movingBoundsWorld,
                includeRootLevel, excludedSubLevelIds, includeTaggedTransparentBlocks, 0.0D);
    }

    // Retain physical bounds separately from padding through rotated SubLevel transforms
    public static SweptBoundsScan beginSweptBoundsBlockingDistanceScan(
            Level rootLevel, @Nullable Object containingSubLevel,
            Vec3 anchorStartWorld, Vec3 directionWorld, double maxDistance,
            List<AABB> movingBoundsWorld, boolean includeRootLevel,
            Set<UUID> excludedSubLevelIds, boolean includeTaggedTransparentBlocks,
            double initialContactAllowance
    ){
        return beginSweptBoundsScan(rootLevel, containingSubLevel, anchorStartWorld, directionWorld,
                maxDistance, movingBoundsWorld, includeRootLevel, excludedSubLevelIds,
                includeTaggedTransparentBlocks, initialContactAllowance, false);
    }

    // Permit only outward departure from initial physical overlap during a bounded exact scan.
    public static SweptBoundsScan beginDepartureBoundsBlockingDistanceScan(
            Level rootLevel, @Nullable Object containingSubLevel,
            Vec3 anchorStartWorld, Vec3 directionWorld, double maxDistance,
            List<AABB> movingBoundsWorld, boolean includeRootLevel,
            Set<UUID> excludedSubLevelIds, boolean includeTaggedTransparentBlocks,
            double initialContactAllowance
    ){
        return beginSweptBoundsScan(rootLevel, containingSubLevel, anchorStartWorld, directionWorld,
                maxDistance, movingBoundsWorld, includeRootLevel, excludedSubLevelIds,
                includeTaggedTransparentBlocks, initialContactAllowance, true);
    }

    private static SweptBoundsScan beginSweptBoundsScan(
            Level rootLevel, @Nullable Object containingSubLevel,
            Vec3 anchorStartWorld, Vec3 directionWorld, double maxDistance,
            List<AABB> movingBoundsWorld, boolean includeRootLevel,
            Set<UUID> excludedSubLevelIds, boolean includeTaggedTransparentBlocks,
            double initialContactAllowance, boolean allowDeparture
    ){
        if (rootLevel == null || anchorStartWorld == null || directionWorld == null
                || directionWorld.lengthSqr() < EPSILON || maxDistance <= 0.0D
                || movingBoundsWorld == null || movingBoundsWorld.isEmpty()) {
            return SweptBoundsScan.complete(Math.max(0.0D, maxDistance));
        }

        Vec3 worldDirection = directionWorld.normalize();
        Vec3 worldDelta = worldDirection.scale(maxDistance);
        Vec3 anchorEndWorld = anchorStartWorld.add(worldDelta);
        AABB sweptWorldBounds = null;
        for (AABB bounds : movingBoundsWorld) {
            if (bounds == null) {
                continue;
            }
            AABB swept = sweptBounds(bounds, worldDelta).inflate(1.0D);
            sweptWorldBounds = sweptWorldBounds == null
                    ? swept : sweptWorldBounds.minmax(swept);
        }
        if (sweptWorldBounds == null) {
            return SweptBoundsScan.complete(maxDistance);
        }

        List<AABB> physicalBounds = allowDeparture ? movingBoundsWorld.stream()
                .filter(java.util.Objects::nonNull).map(bounds -> contactBounds(bounds, initialContactAllowance))
                .filter(java.util.Objects::nonNull).toList() : List.of();
        List<SweptBoundsLevelScan> levelScans = new ArrayList<>();
        if (includeRootLevel) {
            for (AABB bounds : movingBoundsWorld) {
                if (bounds != null) {
                    levelScans.add(new SweptBoundsLevelScan(
                            rootLevel, anchorStartWorld, anchorEndWorld, bounds,
                            includeTaggedTransparentBlocks, contactBounds(bounds, initialContactAllowance), physicalBounds));
                }
            }
        }

        List<Object> subLevels = intersectingSubLevels(
                rootLevel, sweptWorldBounds, null, excludedSubLevelIds);
        if (containingSubLevel != null && !containsIdentity(subLevels, containingSubLevel)) {
            subLevels.add(0, containingSubLevel);
        }
        for (Object subLevel : subLevels) {
            UUID subLevelId = subLevelId(subLevel);
            if (subLevelId != null && excludedSubLevelIds != null
                    && excludedSubLevelIds.contains(subLevelId)) {
                continue;
            }
            BlockGetter subLevelLevel = getSubLevelBlockGetter(subLevel);
            if (subLevelLevel == null) {
                continue;
            }
            Vec3 localStart = toSubLevelPosition(subLevel, anchorStartWorld);
            Vec3 localEnd = toSubLevelPosition(subLevel, anchorEndWorld);
            if (localStart == null || localEnd == null) {
                continue;
            }
            List<AABB> localPhysical = physicalBounds.stream()
                    .map(bounds -> transformWorldBoundsToLocal(subLevel, bounds))
                    .filter(java.util.Objects::nonNull).toList();
            for (AABB worldBounds : movingBoundsWorld) {
                if (worldBounds == null) {
                    continue;
                }
                AABB physical = contactBounds(worldBounds, initialContactAllowance);
                AABB obstacle = subLevelWorldBounds((SubLevel) subLevel);
                if(obstacle == null) continue;
                if(physical != null && (leavesPaddedContact(worldBounds.inflate(COLLISION_MARGIN),
                        physical, obstacle, worldDelta)
                        || translatedBoundsEntryDistance(worldBounds.inflate(COLLISION_MARGIN),
                        obstacle, worldDirection, maxDistance) == null)) continue;
                AABB localBounds = transformWorldBoundsToLocal(subLevel, worldBounds);
                if (localBounds != null) {
                    levelScans.add(new SweptBoundsLevelScan(
                            subLevelLevel, localStart, localEnd, localBounds,
                            includeTaggedTransparentBlocks, physical != null
                            ? transformWorldBoundsToLocal(subLevel, physical)
                            : null, localPhysical));
                }
            }
        }
        return new SweptBoundsScan(maxDistance, levelScans);
    }

    // Find the swept bounds distance in level
    private static @Nullable Double findSweptBoundsDistanceInLevel(
            BlockGetter level,
            Vec3 localStart,
            Vec3 localEnd,
            AABB localBoundsAtStart,
            boolean includeTaggedTransparentBlocks
    ) {
        Vec3 localDelta = localEnd.subtract(localStart);
        if (localDelta.lengthSqr() < EPSILON) {
            return null;
        }

        AABB swept = sweptBounds(localBoundsAtStart, localDelta)
                .inflate(COLLISION_MARGIN);
        int minX = Mth.floor(swept.minX - EPSILON) - 1;
        int minY = Mth.floor(swept.minY - EPSILON) - 1;
        int minZ = Mth.floor(swept.minZ - EPSILON) - 1;
        int maxX = Mth.floor(swept.maxX + EPSILON) + 1;
        int maxY = Mth.floor(swept.maxY + EPSILON) + 1;
        int maxZ = Mth.floor(swept.maxZ + EPSILON) + 1;

        Double nearest = null;
        LoadedBlockLookup lookup = new LoadedBlockLookup(level, false);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minChunkX = minX >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkX = maxX >> 4;
        int maxChunkZ = maxZ >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            int chunkMinX = chunkX << 4;
            int fromX = Math.max(minX, chunkMinX);
            int toX = Math.min(maxX, chunkMinX + 15);
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                int chunkMinZ = chunkZ << 4;
                int fromZ = Math.max(minZ, chunkMinZ);
                int toZ = Math.min(maxZ, chunkMinZ + 15);
                pos.set(chunkMinX, minY, chunkMinZ);
                if (!lookup.moveTo(pos)) {
                    continue;
                }
                for (int x = fromX; x <= toX; x++) {
                    for (int z = fromZ; z <= toZ; z++) {
                        for (int y = minY; y <= maxY; y++) {
                            if(lookup.emptySection(y)){
                                y = Math.min(maxY, (y | 15));
                                continue;
                            }
                            pos.set(x, y, z);
                            nearest = nearestDistance(nearest,
                                    sweptBoundsBlockIntersectionFraction(
                                            level, pos, lookup.blockState(pos), localStart, localEnd,
                                            localBoundsAtStart,
                                            includeTaggedTransparentBlocks));
                            if (nearest != null && nearest <= EPSILON) {
                                return 0.0D;
                            }
                        }
                    }
                }
            }
        }
        return nearest;
    }

    // Get the swept bounds block intersection fraction
    private static @Nullable Double sweptBoundsBlockIntersectionFraction(
            BlockGetter level,
            BlockPos pos,
            Vec3 localStart,
            Vec3 localEnd,
            AABB localBoundsAtStart,
            boolean includeTaggedTransparentBlocks
    ) {
        return sweptBoundsBlockIntersectionFraction(level, pos, level.getBlockState(pos),
                localStart, localEnd, localBoundsAtStart, includeTaggedTransparentBlocks);
    }

    // Use an already loaded chunk state during incremental hull sweeps
    private static @Nullable Double sweptBoundsBlockIntersectionFraction(
            BlockGetter level, BlockPos pos, BlockState state,
            Vec3 localStart, Vec3 localEnd, AABB localBoundsAtStart,
            boolean includeTaggedTransparentBlocks
    ){
        return sweptBoundsBlockIntersectionFraction(level, pos, state, localStart, localEnd,
                localBoundsAtStart, includeTaggedTransparentBlocks, null);
    }

    // Apply the same initial-contact rule to every physical block shape
    private static @Nullable Double sweptBoundsBlockIntersectionFraction(
            BlockGetter level, BlockPos pos, BlockState state,
            Vec3 localStart, Vec3 localEnd, AABB localBoundsAtStart,
            boolean includeTaggedTransparentBlocks, @Nullable AABB physicalBoundsAtStart
    ){
        return sweptBoundsBlockIntersectionFraction(level, pos, state, localStart, localEnd,
                localBoundsAtStart, includeTaggedTransparentBlocks, physicalBoundsAtStart, List.of());
    }

    private static @Nullable Double sweptBoundsBlockIntersectionFraction(
            BlockGetter level, BlockPos pos, BlockState state,
            Vec3 localStart, Vec3 localEnd, AABB localBoundsAtStart,
            boolean includeTaggedTransparentBlocks, @Nullable AABB physicalBoundsAtStart,
            List<AABB> departureBounds
    ){
        if (state.isAir() || (!includeTaggedTransparentBlocks
                && AllTags.AllBlockTags.FAN_TRANSPARENT.matches(state))) {
            return null;
        }
        VoxelShape shape = collisionShape(level, pos, state);
        if (shape.isEmpty()) {
            return null;
        }
        if (shape == Shapes.block()) {
            return sweptBoundsIntersectionFraction(
                    localStart, localEnd, localBoundsAtStart,
                    new AABB(pos), COLLISION_MARGIN, physicalBoundsAtStart, departureBounds);
        }

        double[] nearestShapeHit = {Double.POSITIVE_INFINITY};
        shape.forAllBoxes((shapeMinX, shapeMinY, shapeMinZ,
                           shapeMaxX, shapeMaxY, shapeMaxZ) -> {
            Double hit = sweptBoundsIntersectionFraction(
                    localStart, localEnd, localBoundsAtStart,
                    new AABB(
                            pos.getX() + shapeMinX,
                            pos.getY() + shapeMinY,
                            pos.getZ() + shapeMinZ,
                            pos.getX() + shapeMaxX,
                            pos.getY() + shapeMaxY,
                            pos.getZ() + shapeMaxZ),
                    COLLISION_MARGIN, physicalBoundsAtStart, departureBounds);
            if (hit != null) {
                nearestShapeHit[0] = Math.min(nearestShapeHit[0], hit);
            }
        });
        return Double.isFinite(nearestShapeHit[0]) ? nearestShapeHit[0] : null;
    }

    // Handle the swept bounds scan
    public static final class SweptBoundsScan {
        // Maximum distance
        private final double maxDistance;
        // Tracked level scans
        private final List<SweptBoundsLevelScan> levelScans;
        // Level scan index
        private int levelScanIndex;
        // Current nearest fraction
        private @Nullable Double nearestFraction;
        // Tracks whether swept bounds scan is complete
        private boolean complete;
        // Current result
        private double result;

        // Initialize the swept bounds scan
        private SweptBoundsScan(
                double maxDistance,
                List<SweptBoundsLevelScan> levelScans
        ) {
            this.maxDistance = Math.max(0.0D, maxDistance);
            this.levelScans = List.copyOf(levelScans);
            this.complete = levelScans.isEmpty();
            this.result = this.maxDistance;
        }

        // Complete the swept bounds scan
        private static SweptBoundsScan complete(double res) {
            return new SweptBoundsScan(Math.max(0.0D, res), List.of());
        }

        // Advance the swept bounds scan
        public boolean advance(int blockBudget, long timeBudgetNanos) {
            if (complete) {
                return true;
            }
            int remainingBlocks = Math.max(1, blockBudget);
            long startedNanos = timeBudgetNanos > 0L ? System.nanoTime() : 0L;
            while (remainingBlocks > 0 && !complete) {
                SweptBoundsLevelScan levelScan = levelScans.get(levelScanIndex);
                int processed = levelScan.advance(
                        remainingBlocks, startedNanos, timeBudgetNanos);
                remainingBlocks -= processed;
                if (levelScan.isComplete()) {
                    nearestFraction = nearestDistance(
                            nearestFraction, levelScan.nearestFraction());
                    if (isImmediateHit(nearestFraction, maxDistance)) {
                        complete = true;
                        result = 0.0D;
                        break;
                    }
                    levelScanIndex++;
                    if (levelScanIndex >= levelScans.size()) {
                        complete = true;
                        result = nearestFraction == null
                                ? maxDistance
                                : Mth.clamp(
                                        nearestFraction * maxDistance - 0.0625D,
                                        0.0D, maxDistance);
                    }
                    if(timeBudgetElapsed(startedNanos, timeBudgetNanos)) break;
                    continue;
                }
                if (processed == 0 || timeBudgetElapsed(
                        startedNanos, timeBudgetNanos)) {
                    break;
                }
            }
            return complete;
        }

        // Check if this is complete
        public boolean isComplete() {
            return complete;
        }

        // Reject retained clearance after a block edit in any inspected terrain section
        public boolean isCurrent(){
            for(SweptBoundsLevelScan scan : levelScans) if(!scan.lookup.isCurrent()) return false;
            return true;
        }

        // Get the result
        public double result() {
            return result;
        }

        // Report measured obstruction without treating an unfinished scan as a collision
        public double confirmedBlockingDistance(double fallback){
            if(complete) return result;
            Double nearest = nearestFraction;
            if(levelScanIndex < levelScans.size()){
                nearest = nearestDistance(nearest, levelScans.get(levelScanIndex).nearestFraction());
            }
            return nearest == null ? Math.max(0.0D, fallback)
                    : Math.min(Math.max(0.0D, fallback),
                    Mth.clamp(nearest * maxDistance - 0.0625D, 0.0D, maxDistance));
        }
    }

    // Handle the swept bounds level scan
    private static final class SweptBoundsLevelScan {
        // Level
        private final BlockGetter level;
        private final LoadedBlockLookup lookup;
        // Local start
        private final Vec3 localStart;
        // Local end
        private final Vec3 localEnd;
        // Local bounds at start
        private final AABB localBoundsAtStart;
        // Physical hull before the caller's clearance padding
        private final @Nullable AABB physicalBoundsAtStart;
        private final List<AABB> departureBounds;
        // Controls whether to include tagged transparent blocks
        private final boolean includeTaggedTransparentBlocks;
        // Minimum x
        private final int minX;
        // Minimum y
        private final int minY;
        // Minimum z
        private final int minZ;
        // Maximum x
        private final int maxX;
        // Maximum y
        private final int maxY;
        // Maximum z
        private final int maxZ;
        // Minimum chunk x
        private final int minChunkX;
        // Maximum chunk x
        private final int maxChunkX;
        // Minimum chunk z
        private final int minChunkZ;
        // Maximum chunk z
        private final int maxChunkZ;
        // Swept bounds level scan position
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // Current chunk x
        private int chunkX;
        // Current chunk z
        private int chunkZ;
        // Current from x
        private int fromX;
        // Current to x
        private int toX;
        // Current from z
        private int fromZ;
        // Current to z
        private int toZ;
        // Current swept bounds level scan X coordinate
        private int x;
        // Current swept bounds level scan Y coordinate
        private int y;
        // Current swept bounds level scan Z coordinate
        private int z;
        // Tracks whether chunk is prepared
        private boolean chunkPrepared;
        // Tracks whether swept bounds level scan is complete
        private boolean complete;
        // Current nearest fraction
        private @Nullable Double nearestFraction;

        // Initialize the swept bounds level scan
        private SweptBoundsLevelScan(
                BlockGetter level,
                Vec3 localStart,
                Vec3 localEnd,
                AABB localBoundsAtStart,
                boolean includeTaggedTransparentBlocks,
                @Nullable AABB physicalBoundsAtStart, List<AABB> departureBounds
        ) {
            this.level = level;
            this.lookup = new LoadedBlockLookup(level, false);
            this.localStart = localStart;
            this.localEnd = localEnd;
            this.localBoundsAtStart = localBoundsAtStart;
            this.physicalBoundsAtStart = physicalBoundsAtStart;
            this.departureBounds = departureBounds;
            this.includeTaggedTransparentBlocks = includeTaggedTransparentBlocks;
            Vec3 localDelta = localEnd.subtract(localStart);
            if (localDelta.lengthSqr() < EPSILON) {
                minX = 0;
                minY = 0;
                minZ = 0;
                maxX = -1;
                maxY = -1;
                maxZ = -1;
                minChunkX = 0;
                maxChunkX = -1;
                minChunkZ = 0;
                maxChunkZ = -1;
                complete = true;
                return;
            }
            AABB swept = sweptBounds(localBoundsAtStart, localDelta)
                    .inflate(COLLISION_MARGIN);
            minX = Mth.floor(swept.minX - EPSILON) - 1;
            minY = Mth.floor(swept.minY - EPSILON) - 1;
            minZ = Mth.floor(swept.minZ - EPSILON) - 1;
            maxX = Mth.floor(swept.maxX + EPSILON) + 1;
            maxY = Mth.floor(swept.maxY + EPSILON) + 1;
            maxZ = Mth.floor(swept.maxZ + EPSILON) + 1;
            minChunkX = minX >> 4;
            maxChunkX = maxX >> 4;
            minChunkZ = minZ >> 4;
            maxChunkZ = maxZ >> 4;
            chunkX = minChunkX;
            chunkZ = minChunkZ;
        }

        // Advance the swept bounds level scan
        private int advance(
                int blockBudget,
                long startedNanos,
                long timeBudgetNanos
        ) {
            int processed = 0;
            while (!complete && processed < blockBudget) {
                if (processed > 0 && (processed & 31) == 0
                        && timeBudgetElapsed(startedNanos, timeBudgetNanos)) {
                    break;
                }
                if (!chunkPrepared) {
                    if (chunkX > maxChunkX) {
                        complete = true;
                        break;
                    }
                    int chunkMinX = chunkX << 4;
                    int chunkMinZ = chunkZ << 4;
                    fromX = Math.max(minX, chunkMinX);
                    toX = Math.min(maxX, chunkMinX + 15);
                    fromZ = Math.max(minZ, chunkMinZ);
                    toZ = Math.min(maxZ, chunkMinZ + 15);
                    x = fromX;
                    y = minY;
                    z = fromZ;
                    pos.set(chunkMinX, minY, chunkMinZ);
                    chunkPrepared = true;
                    if (!lookup.moveTo(pos)) {
                        processed++;
                        advanceChunk();
                        continue;
                    }
                }

                pos.set(x, y, z);
                int occupiedY = lookup.nextOccupiedY(x, y, z);
                if(occupiedY > y){
                    y = Math.min(maxY, occupiedY - 1);
                    processed++;
                    advanceBlock();
                    continue;
                }
                nearestFraction = nearestDistance(
                        nearestFraction,
                        sweptBoundsBlockIntersectionFraction(
                                level, pos, lookup.blockState(pos), localStart, localEnd,
                                localBoundsAtStart,
                                includeTaggedTransparentBlocks, physicalBoundsAtStart, departureBounds));
                processed++;
                if (nearestFraction != null && nearestFraction <= EPSILON) {
                    complete = true;
                    break;
                }
                advanceBlock();
            }
            return processed;
        }

        // Advance the block
        private void advanceBlock() {
            y++;
            if (y <= maxY) {
                return;
            }
            y = minY;
            z++;
            if (z <= toZ) {
                return;
            }
            z = fromZ;
            x++;
            if (x > toX) {
                advanceChunk();
            }
        }

        // Advance the chunk
        private void advanceChunk() {
            chunkPrepared = false;
            chunkZ++;
            if (chunkZ > maxChunkZ) {
                chunkZ = minChunkZ;
                chunkX++;
            }
        }

        // Check if this is complete
        private boolean isComplete() {
            return complete;
        }

        // Get the nearest fraction
        private @Nullable Double nearestFraction() {
            return nearestFraction;
        }
    }

    // Check if the time budget elapsed
    private static boolean timeBudgetElapsed(
            long startedNanos,
            long timeBudgetNanos
    ) {
        return timeBudgetNanos > 0L
                && System.nanoTime() - startedNanos >= timeBudgetNanos;
    }

    // Get the collision shape
    private static VoxelShape collisionShape(
            BlockGetter level,
            BlockPos pos,
            BlockState state
    ) {
        if (state.getBlock() instanceof BlockSubLevelCollisionShape extension) {
            return extension.getSubLevelCollisionShape(level, state);
        }
        return state.getCollisionShape(level, pos, SableCollisionContext.get());
    }

    // Check if the hit is immediate
    private static boolean isImmediateHit(@Nullable Double fraction, double distance) {
        return fraction != null
                && fraction * Math.max(0.0D, distance) <= 0.0625D + EPSILON;
    }

    // Get the swept bounds intersection fraction
    static @Nullable Double sweptBoundsIntersectionFraction(
            Vec3 anchorStart,
            Vec3 anchorEnd,
            AABB movingBoundsAtStart,
            AABB obstacleBounds,
            double margin
    ) {
        return sweptBoundsIntersectionFraction(anchorStart, anchorEnd, movingBoundsAtStart,
                obstacleBounds, margin, null);
    }

    // Ignore only initial padding contact whose physical separation cannot decrease
    static @Nullable Double sweptBoundsIntersectionFraction(
            Vec3 anchorStart, Vec3 anchorEnd, AABB movingBoundsAtStart,
            AABB obstacleBounds, double margin, @Nullable AABB physicalBoundsAtStart
    ){
        return sweptBoundsIntersectionFraction(anchorStart, anchorEnd, movingBoundsAtStart,
                obstacleBounds, margin, physicalBoundsAtStart, List.of());
    }

    private static @Nullable Double sweptBoundsIntersectionFraction(
            Vec3 anchorStart, Vec3 anchorEnd, AABB movingBoundsAtStart,
            AABB obstacleBounds, double margin, @Nullable AABB physicalBoundsAtStart,
            List<AABB> departureBounds
    ){
        Vec3 delta = anchorEnd.subtract(anchorStart);
        if(allowsOverlapDeparture(departureBounds, obstacleBounds, delta.normalize(), delta.length())) return null;
        if(leavesPaddedContact(movingBoundsAtStart.inflate(Math.max(0.0D, margin)),
                physicalBoundsAtStart, obstacleBounds, anchorEnd.subtract(anchorStart))) return null;
        double minOffsetX = movingBoundsAtStart.minX - anchorStart.x;
        double minOffsetY = movingBoundsAtStart.minY - anchorStart.y;
        double minOffsetZ = movingBoundsAtStart.minZ - anchorStart.z;
        double maxOffsetX = movingBoundsAtStart.maxX - anchorStart.x;
        double maxOffsetY = movingBoundsAtStart.maxY - anchorStart.y;
        double maxOffsetZ = movingBoundsAtStart.maxZ - anchorStart.z;
        AABB expanded = new AABB(
                obstacleBounds.minX - maxOffsetX,
                obstacleBounds.minY - maxOffsetY,
                obstacleBounds.minZ - maxOffsetZ,
                obstacleBounds.maxX - minOffsetX,
                obstacleBounds.maxY - minOffsetY,
                obstacleBounds.maxZ - minOffsetZ
        ).inflate(Math.max(0.0D, margin));
        Vec3 movement = anchorEnd.subtract(anchorStart);
        Double intersection = intersectSegment(anchorStart, movement, expanded);
        if (intersection != null && intersection <= EPSILON
                && leavesInitialContact(
                anchorStart, movement, expanded, Math.max(0.0D, margin))) {
            return null;
        }
        return intersection;
    }

    // Remove only the clearance padding supplied by the caller
    private static @Nullable AABB contactBounds(AABB bounds, double allowance){
        if(!Double.isFinite(allowance) || allowance <= 0.0D) return null;
        double maximum = Math.min(bounds.getXsize(), Math.min(bounds.getYsize(), bounds.getZsize())) * 0.5D;
        return allowance < maximum ? bounds.deflate(allowance) : null;
    }

    // Preserve a separating axis instead of treating an existing safety overlap as a new collision
    private static boolean leavesPaddedContact(AABB padded, @Nullable AABB physical,
            AABB obstacle, Vec3 movement){
        if(physical == null || !padded.intersects(obstacle) || movement.lengthSqr() < EPSILON) return false;
        return physical.maxX <= obstacle.minX + EPSILON && movement.x <= 0.0D
                || physical.minX >= obstacle.maxX - EPSILON && movement.x >= 0.0D
                || physical.maxY <= obstacle.minY + EPSILON && movement.y <= 0.0D
                || physical.minY >= obstacle.maxY - EPSILON && movement.y >= 0.0D
                || physical.maxZ <= obstacle.minZ + EPSILON && movement.z <= 0.0D
                || physical.minZ >= obstacle.maxZ - EPSILON && movement.z >= 0.0D;
    }

    // Keep movement blocked into a resting surface while allowing it to leave
    private static boolean leavesInitialContact(
            Vec3 start,
            Vec3 movement,
            AABB bounds,
            double contactMargin
    ) {
        if (!contains(bounds, start) || movement.lengthSqr() < EPSILON) {
            return false;
        }
        double contactDepth = Math.min(
                Math.min(start.x - bounds.minX, bounds.maxX - start.x),
                Math.min(
                        Math.min(start.y - bounds.minY, bounds.maxY - start.y),
                        Math.min(start.z - bounds.minZ, bounds.maxZ - start.z)));
        if (contactDepth > contactMargin + EPSILON) {
            return false;
        }
        double forwardExit = exitFraction(start, movement, bounds);
        double reverseExit = exitFraction(start, movement.scale(-1.0D), bounds);
        return Double.isFinite(forwardExit)
                && forwardExit < 1.0D - EPSILON
                && forwardExit + EPSILON < reverseExit;
    }

    // Check if this contains the value
    private static boolean contains(AABB bounds, Vec3 point) {
        return point.x >= bounds.minX - EPSILON && point.x <= bounds.maxX + EPSILON
                && point.y >= bounds.minY - EPSILON && point.y <= bounds.maxY + EPSILON
                && point.z >= bounds.minZ - EPSILON && point.z <= bounds.maxZ + EPSILON;
    }

    // Get the exit fraction
    private static double exitFraction(Vec3 start, Vec3 movement, AABB bounds) {
        double exit = Double.POSITIVE_INFINITY;
        if (movement.x > EPSILON) {
            exit = Math.min(exit, (bounds.maxX - start.x) / movement.x);
        } else if (movement.x < -EPSILON) {
            exit = Math.min(exit, (bounds.minX - start.x) / movement.x);
        }
        if (movement.y > EPSILON) {
            exit = Math.min(exit, (bounds.maxY - start.y) / movement.y);
        } else if (movement.y < -EPSILON) {
            exit = Math.min(exit, (bounds.minY - start.y) / movement.y);
        }
        if (movement.z > EPSILON) {
            exit = Math.min(exit, (bounds.maxZ - start.z) / movement.z);
        } else if (movement.z < -EPSILON) {
            exit = Math.min(exit, (bounds.minZ - start.z) / movement.z);
        }
        return exit < -EPSILON ? Double.POSITIVE_INFINITY : Math.max(0.0D, exit);
    }

    // Get the swept bounds
    private static AABB sweptBounds(AABB boundsAtStart, Vec3 delta) {
        return new AABB(
                Math.min(boundsAtStart.minX, boundsAtStart.minX + delta.x),
                Math.min(boundsAtStart.minY, boundsAtStart.minY + delta.y),
                Math.min(boundsAtStart.minZ, boundsAtStart.minZ + delta.z),
                Math.max(boundsAtStart.maxX, boundsAtStart.maxX + delta.x),
                Math.max(boundsAtStart.maxY, boundsAtStart.maxY + delta.y),
                Math.max(boundsAtStart.maxZ, boundsAtStart.maxZ + delta.z));
    }

    // Transform the world bounds to local
    private static @Nullable AABB transformWorldBoundsToLocal(
            Object subLevel,
            AABB worldBounds
    ) {
        if (!(subLevel instanceof SubLevel sableSubLevel)
                || sableSubLevel.isRemoved() || worldBounds == null) {
            return null;
        }
        return new BoundingBox3d(worldBounds)
                .transformInverse(
                        sableSubLevel.logicalPose(), new BoundingBox3d())
                .toMojang();
    }

    // Get the intersecting sub levels
    private static List<Object> intersectingSubLevels(
            Level level,
            AABB worldBounds,
            @Nullable ProbeCache probeCache,
            @Nullable Set<UUID> excludedIds
    ) {
        List<Object> res = new ArrayList<>();
        if (probeCache != null && probeCache.currentBodies(level)) {
            for (Object candidate : probeCache.subLevels) {
                addIntersectingSubLevel(res, candidate, worldBounds, excludedIds);
            }
            return res;
        }
        try {
            for (SubLevel subLevel : Sable.HELPER.getAllIntersecting(
                    level, new BoundingBox3d(worldBounds))) {
                addIntersectingSubLevel(res, subLevel, worldBounds, excludedIds);
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
        // Merge authoritative bodies even when the spatial index has not published their new pose
        List<Object> known;
        if (probeCache != null && probeCache.currentBodies(level)) {
            known = probeCache.subLevels;
        } else {
            known = SubLevelBlockEntityCollector.getSubLevels(level);
            if (probeCache != null) {
                probeCache.subLevelRoot = level;
                List<Object> snapshot = new ArrayList<>(known);
                for (Object candidate : res) {
                    if (!containsIdentity(snapshot, candidate)) snapshot.add(candidate);
                }
                probeCache.subLevels = List.copyOf(snapshot);
                probeCache.subLevelCount = bodyCount(level);
            }
        }
        for (Object candidate : known) {
            addIntersectingSubLevel(res, candidate, worldBounds, excludedIds);
        }
        return res;
    }

    // Retain body identities only; bounds and velocities remain live on every query
    private static List<Object> knownSubLevels(Level level, @Nullable ProbeCache cache){
        if(cache != null && cache.currentBodies(level)) return cache.subLevels;
        List<Object> bodies = SubLevelBlockEntityCollector.getSubLevels(level);
        if(cache != null){ cache.subLevelRoot = level; cache.subLevels = List.copyOf(bodies); cache.subLevelCount = bodyCount(level); }
        return bodies;
    }

    private static int bodyCount(Level level){
        SubLevelContainer container = level == null ? null : SubLevelContainer.getContainer(level);
        return container == null ? -1 : container.getAllSubLevels().size();
    }

    // Get collision envelopes for every non-excluded Sable body in one query.
    private static List<AABB> subLevelWorldBounds(
            List<Object> subLevels,
            Set<UUID> excludedSubLevelIds
    ) {
        if (subLevels == null || subLevels.isEmpty()) return List.of();
        List<AABB> bounds = new ArrayList<>(subLevels.size());
        for (Object candidate : subLevels) {
            if (!(candidate instanceof SubLevel subLevel) || subLevel.isRemoved()) continue;
            UUID id = subLevel.getUniqueId();
            if (excludedSubLevelIds != null && excludedSubLevelIds.contains(id)) continue;
            AABB live = subLevelWorldBounds(subLevel);
            if(live != null) bounds.add(live);
        }
        return List.copyOf(bounds);
    }

    // Preserve world separation before a rotated local AABB expands the hull
    private static @Nullable AABB subLevelWorldBounds(SubLevel subLevel){
        return SableAssemblyHullApi.worldBounds(subLevel);
    }

    // Get the first distance at which one translated AABB overlaps a fixed AABB.
    private static @Nullable Double translatedBoundsEntryDistance(
            AABB moving,
            AABB obstacle,
            Vec3 direction,
            double maxDistance
    ) {
        double entry = 0.0D;
        double exit = Math.max(0.0D, maxDistance);
        for (int axis = 0; axis < 3; axis++) {
            double movingMin = axis == 0 ? moving.minX : axis == 1 ? moving.minY : moving.minZ;
            double movingMax = axis == 0 ? moving.maxX : axis == 1 ? moving.maxY : moving.maxZ;
            double obstacleMin = axis == 0 ? obstacle.minX : axis == 1 ? obstacle.minY : obstacle.minZ;
            double obstacleMax = axis == 0 ? obstacle.maxX : axis == 1 ? obstacle.maxY : obstacle.maxZ;
            double velocity = axis == 0 ? direction.x : axis == 1 ? direction.y : direction.z;
            if (Math.abs(velocity) <= EPSILON) {
                if (movingMax < obstacleMin || movingMin > obstacleMax) {
                    return null;
                }
                continue;
            }
            double axisEntry;
            double axisExit;
            if (velocity > 0.0D) {
                axisEntry = (obstacleMin - movingMax) / velocity;
                axisExit = (obstacleMax - movingMin) / velocity;
            } else {
                axisEntry = (obstacleMax - movingMin) / velocity;
                axisExit = (obstacleMin - movingMax) / velocity;
            }
            entry = Math.max(entry, axisEntry);
            exit = Math.min(exit, axisExit);
            if (entry > exit + EPSILON) return null;
        }
        return exit < -EPSILON || entry > maxDistance + EPSILON
                ? null : Mth.clamp(entry, 0.0D, maxDistance);
    }

    // Add the intersecting sublevel
    private static void addIntersectingSubLevel(
            List<Object> target,
            Object candidate,
            AABB worldBounds,
            @Nullable Set<UUID> excludedIds
    ) {
        if (!(candidate instanceof SubLevel subLevel) || subLevel.isRemoved()
                || excludedIds != null && excludedIds.contains(subLevel.getUniqueId())
                || containsIdentity(target, subLevel)) {
            return;
        }
        AABB bounds = subLevelWorldBounds(subLevel);
        if(bounds == null || !bounds.intersects(worldBounds)) return;
        target.add(subLevel);
    }

    // Get the sublevel id
    private static @Nullable UUID subLevelId(Object subLevel) {
        return subLevel instanceof SubLevel sableSubLevel
                && !sableSubLevel.isRemoved()
                ? sableSubLevel.getUniqueId() : null;
    }

    // Convert the sub level particle occlusion to sublevel position
    private static @Nullable Vec3 toSubLevelPosition(
            Object subLevel,
            Vec3 worldPosition
    ) {
        if (!(subLevel instanceof SubLevel sableSubLevel)
                || sableSubLevel.isRemoved() || worldPosition == null) {
            return null;
        }
        return sableSubLevel.logicalPose()
                .transformPositionInverse(worldPosition);
    }

    // Convert the sub level particle occlusion to world position
    private static @Nullable Vec3 toWorldPosition(
            Object subLevel,
            Vec3 plotPosition
    ) {
        if (!(subLevel instanceof SubLevel sableSubLevel)
                || sableSubLevel.isRemoved() || plotPosition == null) {
            return null;
        }
        return sableSubLevel.logicalPose().transformPosition(plotPosition);
    }

    // Check if this contains identity
    private static boolean containsIdentity(List<Object> values, Object candidate) {
        for (Object val : values) {
            if (val == candidate) {
                return true;
            }
        }
        return false;
    }

    // Find the blocking distance in level
    private static @Nullable Double findBlockingDistanceInLevel(BlockGetter level, @Nullable Object subLevel,
            Vec3 localStart, Vec3 localEnd, Vec3 worldStart, Vec3 worldDirection, double maxDistance,
            boolean includeTaggedTransparentBlocks,
            LoadedBlockLookup lookup, double initialContactAllowance,
            @Nullable Predicate<BlockState> blocksView) {
        Vec3 localDelta = localEnd.subtract(localStart);
        if (localDelta.lengthSqr() < EPSILON) {
            return null;
        }

        int currentX = Mth.floor(localStart.x);
        int currentY = Mth.floor(localStart.y);
        int currentZ = Mth.floor(localStart.z);
        int endX = Mth.floor(localEnd.x);
        int endY = Mth.floor(localEnd.y);
        int endZ = Mth.floor(localEnd.z);
        int stepX = Integer.compare(endX, currentX);
        int stepY = Integer.compare(endY, currentY);
        int stepZ = Integer.compare(endZ, currentZ);
        double tMaxX = firstBoundaryT(localStart.x, localDelta.x, currentX, stepX);
        double tMaxY = firstBoundaryT(localStart.y, localDelta.y, currentY, stepY);
        double tMaxZ = firstBoundaryT(localStart.z, localDelta.z, currentZ, stepZ);
        double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : 1.0D / Math.abs(localDelta.x);
        double tDeltaY = stepY == 0 ? Double.POSITIVE_INFINITY : 1.0D / Math.abs(localDelta.y);
        double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : 1.0D / Math.abs(localDelta.z);

        Double nearest = null;
        long maxSteps = Math.abs((long) endX - currentX)
                + Math.abs((long) endY - currentY)
                + Math.abs((long) endZ - currentZ) + 4L;
        BlockPos.MutableBlockPos current = new BlockPos.MutableBlockPos();
        for (long step = 0L; step < maxSteps; step++) {
            current.set(currentX, currentY, currentZ);
            if (!lookup.moveTo(current)) {
                return null;
            }
            Double distance = findBlockShapeDistance(level, lookup, subLevel, current,
                    localStart, localDelta, worldStart, worldDirection,
                    maxDistance, includeTaggedTransparentBlocks, initialContactAllowance, blocksView);
            nearest = nearestDistance(nearest, distance);
            if (nearest != null) {
                return nearest;
            }
            if (currentX == endX && currentY == endY && currentZ == endZ) {
                return null;
            }

            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                currentX += stepX;
                tMaxX += tDeltaX;
            } else if (tMaxY <= tMaxZ) {
                currentY += stepY;
                tMaxY += tDeltaY;
            } else {
                currentZ += stepZ;
                tMaxZ += tDeltaZ;
            }
        }

        return nearest;
    }

    // Find the first non-air root-level block without resolving collision shapes
    private static double findAirOnlyBlockingDistanceInLevel(
            LoadedBlockLookup lookup,
            Vec3 start,
            Vec3 direction,
            double maxDistance,
            AABB movingBoundsAtStart,
            @Nullable AABB physicalBoundsAtStart,
            List<AABB> departureBounds
    ) {
        Vec3 end = start.add(direction.scale(maxDistance));
        int currentX = Mth.floor(start.x);
        int currentY = Mth.floor(start.y);
        int currentZ = Mth.floor(start.z);
        int endX = Mth.floor(end.x);
        int endY = Mth.floor(end.y);
        int endZ = Mth.floor(end.z);
        int stepX = Integer.compare(endX, currentX);
        int stepY = Integer.compare(endY, currentY);
        int stepZ = Integer.compare(endZ, currentZ);
        double tMaxX = firstBoundaryT(start.x, direction.x, currentX, stepX);
        double tMaxY = firstBoundaryT(start.y, direction.y, currentY, stepY);
        double tMaxZ = firstBoundaryT(start.z, direction.z, currentZ, stepZ);
        double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : 1.0D / Math.abs(direction.x);
        double tDeltaY = stepY == 0 ? Double.POSITIVE_INFINITY : 1.0D / Math.abs(direction.y);
        double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : 1.0D / Math.abs(direction.z);
        long maximumSteps = Math.abs((long) endX - currentX)
                + Math.abs((long) endY - currentY)
                + Math.abs((long) endZ - currentZ) + 4L;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double travelled = 0.0D;
        for (long step = 0L; step < maximumSteps; step++) {
            pos.set(currentX, currentY, currentZ);
            if (!lookup.moveTo(pos)) return maxDistance;
            if (!lookup.emptySection(currentY) && !lookup.blockState(pos).isAir()) {
                if(!leavesPaddedContact(movingBoundsAtStart.inflate(COLLISION_MARGIN * 2.0D),
                        physicalBoundsAtStart, new AABB(pos), direction)
                        && !allowsOverlapDeparture(departureBounds, new AABB(pos), direction, maxDistance)){
                    return Mth.clamp(travelled - COLLISION_MARGIN, 0.0D, maxDistance);
                }
            }
            if (currentX == endX && currentY == endY && currentZ == endZ) break;
            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                travelled = tMaxX;
                currentX += stepX;
                tMaxX += tDeltaX;
            } else if (tMaxY <= tMaxZ) {
                travelled = tMaxY;
                currentY += stepY;
                tMaxY += tDeltaY;
            } else {
                travelled = tMaxZ;
                currentZ += stepZ;
                tMaxZ += tDeltaZ;
            }
            if (travelled > maxDistance + EPSILON) break;
        }
        return maxDistance;
    }

    // Find the block shape distance
    private static @Nullable Double findBlockShapeDistance(
            BlockGetter level,
            LoadedBlockLookup lookup,
            @Nullable Object subLevel,
            BlockPos pos,
            Vec3 localStart,
            Vec3 localDelta,
            Vec3 worldStart,
            Vec3 worldDirection,
            double maxDistance,
            boolean includeTaggedTransparentBlocks,
            double initialContactAllowance,
            @Nullable Predicate<BlockState> blocksView
    ) {
        VoxelShape shape = lookup.collisionShape(pos, includeTaggedTransparentBlocks);
        if (shape.isEmpty()) {
            return null;
        }
        if (blocksView != null && !blocksView.test(lookup.blockState(pos))) return null;

        double[] nearestT = {Double.POSITIVE_INFINITY};
        if (shape == Shapes.block()) {
            Double hit = contactAwareIntersection(localStart, localDelta,
                    new AABB(pos).inflate(COLLISION_MARGIN), initialContactAllowance);
            if (hit != null) nearestT[0] = hit;
        } else {
            shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
                Double hit = contactAwareIntersection(localStart, localDelta,
                        new AABB(
                                pos.getX() + minX, pos.getY() + minY, pos.getZ() + minZ,
                                pos.getX() + maxX, pos.getY() + maxY, pos.getZ() + maxZ)
                                .inflate(COLLISION_MARGIN), initialContactAllowance);
                if (hit != null) nearestT[0] = Math.min(nearestT[0], hit);
            });
        }
        if (!Double.isFinite(nearestT[0])) return null;
        if (subLevel != null) {
            // Use the rigid Sable pose fraction as world-space distance
            return Mth.clamp(nearestT[0] * maxDistance, 0.0D, maxDistance);
        }
        Vec3 localHit = localStart.add(localDelta.scale(nearestT[0]));
        Vec3 worldHit = level instanceof Level rootLevel
                ? Sable.HELPER.projectOutOfSubLevel(rootLevel, localHit)
                : localHit;
        if (worldHit == null) worldHit = localHit;
        double distance = worldHit.subtract(worldStart).dot(worldDirection);
        return distance >= -EPSILON && distance <= maxDistance + EPSILON
                ? Math.max(0.0D, distance) : null;
    }

    // Do not treat a shallow contact that is being left as a new obstruction
    static @Nullable Double contactAwareIntersection(Vec3 start, Vec3 delta, AABB bounds, double allowance){
        Double hit = intersectSegment(start, delta, bounds);
        return allowance > 0.0D && hit != null && hit <= EPSILON
                && leavesInitialContact(start, delta, bounds, allowance) ? null : hit;
    }

    // Get the intersect segment
    private static @Nullable Double intersectSegment(Vec3 start, Vec3 delta, AABB box) {
        double tMin = 0.0D;
        double tMax = 1.0D;

        if (Math.abs(delta.x) < EPSILON) {
            if (start.x < box.minX || start.x > box.maxX) return null;
        } else {
            double first = (box.minX - start.x) / delta.x;
            double second = (box.maxX - start.x) / delta.x;
            if (first > second) {
                double swap = first;
                first = second;
                second = swap;
            }
            tMin = Math.max(tMin, first);
            tMax = Math.min(tMax, second);
            if (tMin > tMax) return null;
        }
        if (Math.abs(delta.y) < EPSILON) {
            if (start.y < box.minY || start.y > box.maxY) return null;
        } else {
            double first = (box.minY - start.y) / delta.y;
            double second = (box.maxY - start.y) / delta.y;
            if (first > second) {
                double swap = first;
                first = second;
                second = swap;
            }
            tMin = Math.max(tMin, first);
            tMax = Math.min(tMax, second);
            if (tMin > tMax) return null;
        }
        if (Math.abs(delta.z) < EPSILON) {
            if (start.z < box.minZ || start.z > box.maxZ) return null;
        } else {
            double first = (box.minZ - start.z) / delta.z;
            double second = (box.maxZ - start.z) / delta.z;
            if (first > second) {
                double swap = first;
                first = second;
                second = swap;
            }
            tMin = Math.max(tMin, first);
            tMax = Math.min(tMax, second);
            if (tMin > tMax) return null;
        }

        return Mth.clamp(tMin, 0.0D, 1.0D);
    }

    // Get the loaded block lookup
    private static LoadedBlockLookup loadedBlockLookup(
            BlockGetter level,
            @Nullable Map<BlockGetter, LoadedBlockLookup> sharedLookups
    ) {
        if (sharedLookups == null) {
            return new LoadedBlockLookup(level, false);
        }
        return sharedLookups.computeIfAbsent(
                level, blockGetter -> new LoadedBlockLookup(blockGetter, true));
    }

    // Handle the loaded block lookup
    private static final class LoadedBlockLookup {
        // Level
        private final BlockGetter level;
        // Controls whether to cache queries
        private final boolean cacheQueries;
        // Current chunk x
        private int chunkX = Integer.MIN_VALUE;
        // Current chunk z
        private int chunkZ = Integer.MIN_VALUE;
        // Tracks whether loaded block lookup is loaded
        private boolean loaded;
        // Current chunk
        private @Nullable LevelChunk chunk;
        // Live section metadata for the current loaded chunk
        private @Nullable LevelChunkSection[] sections;
        private int minSection;
        private boolean sectionMetadataLoaded;
        private final Map<LevelChunkSection, Long> observedSections = new IdentityHashMap<>();
        private final Map<Long, LevelChunk> observedChunks = new java.util.HashMap<>();
        // Current chunk keys
        private long[] chunkKeys = new long[8];
        // Current chunk states
        private byte[] chunkStates = new byte[8];
        // Current chunks
        private LevelChunk[] chunks = new LevelChunk[8];
        // Chunk count
        private int chunkCount;
        // Current shape keys
        private long[] shapeKeys;
        // Current shape slots
        private boolean[] shapeSlots;
        // Current shape values
        private VoxelShape[] shapeValues;
        // Tracks whether transparent mode is cached
        private @Nullable Boolean cachedTransparentMode;

        // Initialize the loaded block lookup
        private LoadedBlockLookup(BlockGetter level, boolean cacheQueries) {
            this.level = level;
            this.cacheQueries = cacheQueries;
            this.shapeKeys = cacheQueries ? new long[PROBE_SHAPE_CACHE_CAPACITY] : new long[0];
            this.shapeSlots = cacheQueries ? new boolean[PROBE_SHAPE_CACHE_CAPACITY] : new boolean[0];
            this.shapeValues = cacheQueries ? new VoxelShape[PROBE_SHAPE_CACHE_CAPACITY] : new VoxelShape[0];
        }

        // Move the loaded block lookup
        private boolean moveTo(BlockPos pos) {
            int nextChunkX = pos.getX() >> 4;
            int nextChunkZ = pos.getZ() >> 4;
            if (nextChunkX == chunkX && nextChunkZ == chunkZ) return loaded;
            chunkX = nextChunkX;
            chunkZ = nextChunkZ;
            chunk = null;
            sections = null;
            sectionMetadataLoaded = false;
            long chunkKey = (long) chunkX & 0xffffffffL
                    | ((long) chunkZ & 0xffffffffL) << 32;
            if (cacheQueries) {
                for (int idx = 0; idx < chunkCount; idx++) {
                    if (chunkKeys[idx] == chunkKey) {
                        loaded = chunkStates[idx] == 1;
                        chunk = chunks[idx];
                        return loaded;
                    }
                }
            }
            if (level instanceof Level world) {
                var source = world.getChunkSource();
                chunk = source == null ? null : source.getChunkNow(chunkX, chunkZ);
                loaded = source == null ? world.isLoaded(pos) : chunk != null;
            } else if (level instanceof LevelReader reader) {
                loaded = reader.hasChunkAt(pos);
            } else {
                loaded = true;
            }
            if (cacheQueries) {
                rememberChunk(chunkKey);
            }else if(level instanceof Level) observedChunks.putIfAbsent(chunkKey, chunk);
            return loaded;
        }

        // Remember the chunk
        private void rememberChunk(long key) {
            if (chunkCount == chunkKeys.length) {
                int expanded = chunkCount * 2;
                chunkKeys = Arrays.copyOf(chunkKeys, expanded);
                chunkStates = Arrays.copyOf(chunkStates, expanded);
                chunks = Arrays.copyOf(chunks, expanded);
            }
            chunkKeys[chunkCount] = key;
            chunkStates[chunkCount] = loaded ? (byte) 1 : (byte) 2;
            chunks[chunkCount] = chunk;
            chunkCount++;
        }

        // Get the block state
        private BlockState blockState(BlockPos pos) {
            return chunk == null ? level.getBlockState(pos) : chunk.getBlockState(pos);
        }

        private boolean isCurrent(){
            for(var entry : observedSections.entrySet()){
                if(((CollisionBlockIndex.Revision)entry.getKey()).collisionBlockRevision() != entry.getValue()) return false;
            }
            if(level instanceof Level world && world.getChunkSource() != null){
                for(var entry : observedChunks.entrySet()){
                    long key = entry.getKey();
                    if(world.getChunkSource().getChunkNow((int)key, (int)(key >> 32)) != entry.getValue()) return false;
                }
            }
            return true;
        }

        // Skip known empty column spans across repeated hull and route sweeps
        private int nextOccupiedY(int x, int y, int z){
            if(chunk == null) return y;
            if(!sectionMetadataLoaded) emptySection(y);
            int idx = (y >> 4) - minSection;
            if(sections == null || idx < 0 || idx >= sections.length) return (y | 15) + 1;
            LevelChunkSection section = sections[idx];
            if(section instanceof CollisionBlockIndex.Revision tracked){
                observedSections.putIfAbsent(section, tracked.collisionBlockRevision());
            }
            return section == null ? y : (y & ~15) + CollisionBlockIndex.nextOccupiedY(section, x, y, z);
        }

        // Skip air sections and out-of-height positions without reading their palettes
        private boolean emptySection(int y){
            if(chunk == null) return false;
            if(!sectionMetadataLoaded){
                sections = chunk.getSections();
                minSection = chunk.getMinSection();
                sectionMetadataLoaded = true;
            }
            if(sections == null || sections.length == 0) return false;
            int idx = (y >> 4) - minSection;
            if(idx < 0 || idx >= sections.length) return true;
            var section = sections[idx];
            return section != null && section.hasOnlyAir();
        }

        // Get the collision shape
        private VoxelShape collisionShape(
                BlockPos pos,
                boolean includeTaggedTransparentBlocks
        ) {
            if (!cacheQueries) {
                return resolveCollisionShape(pos, includeTaggedTransparentBlocks);
            }
            if (cachedTransparentMode == null
                    || cachedTransparentMode != includeTaggedTransparentBlocks) {
                clearShapeCache();
                cachedTransparentMode = includeTaggedTransparentBlocks;
            }
            long key = pos.asLong();
            VoxelShape cached = cachedShape(key);
            if (cached != null) {
                return cached;
            }
            VoxelShape resolved = resolveCollisionShape(
                    pos, includeTaggedTransparentBlocks);
            cacheShape(key, resolved);
            return resolved;
        }

        // Get the cached shape
        private @Nullable VoxelShape cachedShape(long key) {
            int mask = shapeKeys.length - 1;
            int slot = mixKey(key) & mask;
            return shapeSlots[slot] && shapeKeys[slot] == key
                    ? shapeValues[slot] : null;
        }

        // Cache the shape
        private void cacheShape(long key, VoxelShape shape) {
            int mask = shapeKeys.length - 1;
            int slot = mixKey(key) & mask;
            shapeSlots[slot] = true;
            shapeKeys[slot] = key;
            shapeValues[slot] = shape;
        }

        // Clear the shape cache
        private void clearShapeCache() {
            Arrays.fill(shapeSlots, false);
            Arrays.fill(shapeValues, null);
        }

        // Get the mix key
        private static int mixKey(long val) {
            val ^= val >>> 33;
            val *= 0xff51afd7ed558ccdL;
            val ^= val >>> 33;
            val *= 0xc4ceb9fe1a85ec53L;
            val ^= val >>> 33;
            return (int) val;
        }

        // Resolve the collision shape
        private VoxelShape resolveCollisionShape(
                BlockPos pos,
                boolean includeTaggedTransparentBlocks
        ) {
            BlockState state = blockState(pos);
            if (state.isAir() || (!includeTaggedTransparentBlocks
                    && AllTags.AllBlockTags.FAN_TRANSPARENT.matches(state))) {
                return Shapes.empty();
            }
            return SubLevelParticleOcclusion.collisionShape(level, pos, state);
        }
    }

    // Get the first boundary t
    private static double firstBoundaryT(double start, double delta, int block, int step) {
        if (step == 0 || Math.abs(delta) < EPSILON) {
            return Double.POSITIVE_INFINITY;
        }
        double boundary = step > 0 ? block + 1.0D : block;
        return Math.max(0.0D, (boundary - start) / delta);
    }

    // Get the nearest distance
    private static @Nullable Double nearestDistance(@Nullable Double current, @Nullable Double candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || candidate < current ? candidate : current;
    }

    // Check if this is loaded
    private static boolean isLoaded(BlockGetter level, BlockPos pos) {
        if (level instanceof Level world) {
            return world.isLoaded(pos);
        }
        return !(level instanceof LevelReader reader) || reader.hasChunkAt(pos);
    }

    // Get the sublevel block getter
    private static @Nullable BlockGetter getSubLevelBlockGetter(
            @Nullable Object subLevel
    ) {
        return subLevel instanceof SubLevel sableSubLevel
                && !sableSubLevel.isRemoved()
                ? sableSubLevel.getLevel() : null;
    }
}
