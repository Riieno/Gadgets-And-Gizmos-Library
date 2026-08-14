package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.AllTags;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import dev.ryanhcode.sable.api.physics.collider.SableCollisionContext;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
        if (rootLevel == null || startWorld == null || directionWorld == null
                || directionWorld.lengthSqr() < EPSILON || maxDistance <= 0.0D) {
            return Math.max(0.0D, maxDistance);
        }
        Vec3 worldDirection = directionWorld.normalize();
        Vec3 endWorld = startWorld.add(worldDirection.scale(maxDistance));
        AABB worldBounds = new AABB(startWorld, endWorld).inflate(1.0D);
        List<Object> subLevels = intersectingSubLevels(rootLevel, worldBounds);
        if (containingSubLevel != null && !containsIdentity(subLevels, containingSubLevel)) {
            subLevels.add(0, containingSubLevel);
        }
        return findBlockingDistanceAcrossLevels(
                rootLevel, startWorld, endWorld, worldDirection, maxDistance,
                includeRootLevel, excludedSubLevelIds,
                includeTaggedTransparentBlocks, subLevels, null);
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
        if (!includeRootLevel && subLevels.isEmpty()) {
            return maxDistance;
        }

        Double nearest = includeRootLevel
                ? findBlockingDistanceInLevel(rootLevel, null, startWorld, endWorld,
                        startWorld, worldDirection, maxDistance, includeTaggedTransparentBlocks,
                        loadedBlockLookup(rootLevel, sharedLookups))
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
                    loadedBlockLookup(subLevelLevel, sharedLookups));
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
        List<Object> subLevels = intersectingSubLevels(rootLevel, queryBounds);
        if (containingSubLevel != null
                && !containsIdentity(subLevels, containingSubLevel)) {
            subLevels.add(0, containingSubLevel);
        }
        Map<BlockGetter, LoadedBlockLookup> sharedLookups = probeCache == null
                ? new IdentityHashMap<>() : probeCache.lookups;
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
                        includeTaggedTransparentBlocks, subLevels, sharedLookups);
                nearest = Math.min(nearest, distance);
                if (nearest <= EPSILON) {
                    return 0.0D;
                }
            }
        }
        return Mth.clamp(nearest, 0.0D, maxDistance);
    }

    // Handle the probe cache
    public static final class ProbeCache {
        // Tracked lookups
        private final Map<BlockGetter, LoadedBlockLookup> lookups =
                new IdentityHashMap<>();

        // Clear the probe cache
        public void clear() {
            lookups.clear();
        }
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
                rootLevel, sweptWorldBounds);
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

        List<SweptBoundsLevelScan> levelScans = new ArrayList<>();
        if (includeRootLevel) {
            for (AABB bounds : movingBoundsWorld) {
                if (bounds != null) {
                    levelScans.add(new SweptBoundsLevelScan(
                            rootLevel, anchorStartWorld, anchorEndWorld, bounds,
                            includeTaggedTransparentBlocks));
                }
            }
        }

        List<Object> subLevels = intersectingSubLevels(
                rootLevel, sweptWorldBounds);
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
            for (AABB worldBounds : movingBoundsWorld) {
                if (worldBounds == null) {
                    continue;
                }
                AABB localBounds = transformWorldBoundsToLocal(subLevel, worldBounds);
                if (localBounds != null) {
                    levelScans.add(new SweptBoundsLevelScan(
                            subLevelLevel, localStart, localEnd, localBounds,
                            includeTaggedTransparentBlocks));
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
                if (!isLoaded(level, pos)) {
                    continue;
                }
                for (int x = fromX; x <= toX; x++) {
                    for (int z = fromZ; z <= toZ; z++) {
                        for (int y = minY; y <= maxY; y++) {
                            pos.set(x, y, z);
                            nearest = nearestDistance(nearest,
                                    sweptBoundsBlockIntersectionFraction(
                                            level, pos, localStart, localEnd,
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
        BlockState state = level.getBlockState(pos);
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
                    new AABB(pos), COLLISION_MARGIN);
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
                    COLLISION_MARGIN);
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

        // Get the result
        public double result() {
            return result;
        }
    }

    // Handle the swept bounds level scan
    private static final class SweptBoundsLevelScan {
        // Level
        private final BlockGetter level;
        // Local start
        private final Vec3 localStart;
        // Local end
        private final Vec3 localEnd;
        // Local bounds at start
        private final AABB localBoundsAtStart;
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
                boolean includeTaggedTransparentBlocks
        ) {
            this.level = level;
            this.localStart = localStart;
            this.localEnd = localEnd;
            this.localBoundsAtStart = localBoundsAtStart;
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
                    if (!isLoaded(level, pos)) {
                        advanceChunk();
                        continue;
                    }
                }

                pos.set(x, y, z);
                nearestFraction = nearestDistance(
                        nearestFraction,
                        sweptBoundsBlockIntersectionFraction(
                                level, pos, localStart, localEnd,
                                localBoundsAtStart,
                                includeTaggedTransparentBlocks));
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
            AABB worldBounds
    ) {
        List<Object> res = new ArrayList<>();
        boolean spatialQuerySucceeded = false;
        try {
            for (SubLevel subLevel : Sable.HELPER.getAllIntersecting(
                    level, new BoundingBox3d(worldBounds))) {
                addIntersectingSubLevel(res, subLevel, worldBounds);
            }
            spatialQuerySucceeded = true;
        } catch (RuntimeException | LinkageError ignored) {
        }
        if (!spatialQuerySucceeded) {
            for (Object candidate : SubLevelBlockEntityCollector.getSubLevels(level)) {
                addIntersectingSubLevel(res, candidate, worldBounds);
            }
        }
        return res;
    }

    // Add the intersecting sublevel
    private static void addIntersectingSubLevel(
            List<Object> target,
            Object candidate,
            AABB worldBounds
    ) {
        if (!(candidate instanceof SubLevel subLevel) || subLevel.isRemoved()
                || !subLevel.boundingBox().intersects(worldBounds)
                || containsIdentity(target, subLevel)) {
            return;
        }
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
            LoadedBlockLookup lookup) {
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
                    maxDistance, includeTaggedTransparentBlocks);
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
            boolean includeTaggedTransparentBlocks
    ) {
        VoxelShape shape = lookup.collisionShape(pos, includeTaggedTransparentBlocks);
        if (shape.isEmpty()) {
            return null;
        }

        double[] nearestT = {Double.POSITIVE_INFINITY};
        if (shape == Shapes.block()) {
            Double hit = intersectSegment(localStart, localDelta,
                    new AABB(pos).inflate(COLLISION_MARGIN));
            if (hit != null) nearestT[0] = hit;
        } else {
            shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
                Double hit = intersectSegment(localStart, localDelta,
                        new AABB(
                                pos.getX() + minX, pos.getY() + minY, pos.getZ() + minZ,
                                pos.getX() + maxX, pos.getY() + maxY, pos.getZ() + maxZ)
                                .inflate(COLLISION_MARGIN));
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
                chunk = world.getChunkSource().getChunkNow(chunkX, chunkZ);
                loaded = chunk != null;
            } else if (level instanceof LevelReader reader) {
                loaded = reader.hasChunkAt(pos);
            } else {
                loaded = true;
            }
            if (cacheQueries) {
                rememberChunk(chunkKey);
            }
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
