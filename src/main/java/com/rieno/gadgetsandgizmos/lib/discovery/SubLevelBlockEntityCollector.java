package com.rieno.gadgetsandgizmos.lib.discovery;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.ryanhcode.sable.sublevel.tracking_points.SubLevelTrackingPointSavedData;
import dev.ryanhcode.sable.sublevel.tracking_points.TrackingPoint;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Read loaded block entities from Sable plots without forcing their chunks to load
public final class SubLevelBlockEntityCollector {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final TicketType<UUID> SUB_LEVEL_LOAD_TICKET = TicketType.create(
            "gadgetsngizmos:sable_sublevel_load", Comparator.comparing(UUID::toString), 40);

    /** A loaded, non-air block in one Sable body. */
    public record LoadedBlock(BlockPos position, BlockState state) {
        public LoadedBlock {
            position = position == null ? BlockPos.ZERO : position.immutable();
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the sub level block entity collector
    private SubLevelBlockEntityCollector() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the sublevel
    public static @Nullable Object getSubLevel(Level level, @Nullable UUID subLevelId) {
        if (level == null || subLevelId == null) {
            return null;
        }

        return findSubLevel(level, subLevelId);
    }

    // Find the sublevel
    private static @Nullable SubLevel findSubLevel(Level level, UUID subLevelId) {
        SubLevelContainer container = getContainer(level);
        if (container == null) {
            return null;
        }

        return container.getSubLevel(subLevelId);
    }

    // Ensure the target loaded
    public static boolean ensureTargetLoaded(Level level, @Nullable UUID subLevelId, @Nullable BlockPos pos) {
        if (level == null || pos == null) {
            return false;
        }

        if (subLevelId != null) {
            Object subLevel = ensureSubLevelLoaded(level, subLevelId);
            return isUsableSubLevel(subLevel, pos);
        }

        if (!isSubLevelPlotPosition(level, pos)) {
            return true;
        }

        processPendingSubLevelLoads(level);
        return isLoadedPlotChunk(level, pos);
    }

    // Check if the target is loaded
    public static boolean isTargetLoaded(Level level, @Nullable UUID subLevelId, @Nullable BlockPos pos) {
        if (level == null || pos == null) {
            return false;
        }

        if (subLevelId != null) {
            return isUsableSubLevel(getSubLevel(level, subLevelId), pos);
        }

        if (isSubLevelPlotPosition(level, pos)) {
            return isLoadedPlotChunk(level, pos);
        }

        return level.isLoaded(pos);
    }

    // Ensure the sublevel loaded
    public static @Nullable Object ensureSubLevelLoaded(Level level, @Nullable UUID subLevelId) {
        if (level == null || subLevelId == null) {
            return null;
        }

        SubLevel subLevel = findSubLevel(level, subLevelId);
        if (isUsableSubLevel(subLevel, null)) {
            return subLevel;
        }

        ServerLevel serverLevel = resolveServerLevel(subLevel);
        requestSubLevelLoad(serverLevel != null ? serverLevel : level, subLevelId);
        subLevel = findSubLevel(level, subLevelId);
        return isUsableSubLevel(subLevel, null) ? subLevel : null;
    }

    // Check if this is a sublevel plot position
    public static boolean isSubLevelPlotPosition(Level level, @Nullable BlockPos pos) {
        if (level == null || pos == null) {
            return false;
        }

        SubLevelContainer container = getContainer(level);
        if (container == null) {
            return false;
        }

        try {
            return container.inBounds(pos);
        } catch (Exception ignored) {
            return false;
        }
    }

    // Check if the plot chunk is loaded
    public static boolean isLoadedPlotChunk(Level level, @Nullable BlockPos pos) {
        if (level == null || pos == null) {
            return false;
        }

        SubLevelContainer container = getContainer(level);
        if (container == null) {
            return false;
        }

        try {
            return !container.inBounds(pos) || container.getChunkHolder(new ChunkPos(pos)) != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    // Get the sub levels
    public static List<Object> getSubLevels(@Nullable Level level) {
        SubLevelContainer container = getContainer(level);
        if (container == null) {
            return List.of();
        }

        return new ArrayList<>(container.getAllSubLevels());
    }

    // Get the container
    private static @Nullable SubLevelContainer getContainer(@Nullable Level level) {
        if (level == null) {
            return null;
        }

        ServerLevel serverLevel = resolveServerLevel(level);
        if (serverLevel != null) {
            try {
                return SubLevelContainer.getContainer(serverLevel);
            } catch (Exception ignored) {
                return null;
            }
        }

        if (level.isClientSide()) {
            try {
                return SubLevelContainer.getContainer(level);
            } catch (Exception ignored) {
                return null;
            }
        }

        return null;
    }

    // Resolve the server level
    private static @Nullable ServerLevel resolveServerLevel(Level level) {
        return SableLevelApi.serverLevel(level);
    }

    // Resolve the server level
    private static @Nullable ServerLevel resolveServerLevel(@Nullable SubLevel subLevel) {
        return SableLevelApi.serverLevel(subLevel);
    }

    // Get the block entity
    public static @Nullable BlockEntity getBlockEntity(@Nullable Object subLevel, @Nullable BlockPos pos) {
        if (!(subLevel instanceof SubLevel sableSubLevel) || pos == null || sableSubLevel.isRemoved()) {
            return null;
        }

        LevelPlot plot = sableSubLevel.getPlot();
        if (plot == null) {
            return null;
        }

        PlotChunkHolder holder = plot.getChunkHolder(plot.toLocal(new ChunkPos(pos)));
        if (holder == null) {
            return null;
        }

        LevelChunk chunk = holder.getChunk();
        if (chunk != null) {
            BlockEntity blockEntity = chunk.getBlockEntity(pos);
            if (isMatchingBlockEntity(blockEntity, pos)) {
                return blockEntity;
            }
        }

        return findActorBlockEntity(plot, pos);
    }

    // Get the block entities
    public static List<BlockEntity> getBlockEntities(@Nullable Object subLevel) {
        Map<BlockPos, BlockEntity> blockEntities = new LinkedHashMap<>();
        if (!(subLevel instanceof SubLevel sableSubLevel) || !isUsableSubLevel(sableSubLevel, null)) {
            return List.of();
        }

        try {
            LevelPlot plot = sableSubLevel.getPlot();
            collectActorBlockEntities(plot, blockEntities);
            for (PlotChunkHolder chunkHolder : plot.getLoadedChunks()) {
                LevelChunk chunk = chunkHolder.getChunk();
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    blockEntities.putIfAbsent(blockEntity.getBlockPos().immutable(), blockEntity);
                }
            }
        } catch (RuntimeException | LinkageError err) {
            return new ArrayList<>(blockEntities.values());
        }

        return new ArrayList<>(blockEntities.values());
    }

    /**
     * Read a bounded snapshot of the non-air blocks already loaded for a Sable
     * body. This never creates chunk tickets and is suitable for previews sent
     * to a client that is not currently tracking the craft itself.
     */
    public static List<LoadedBlock> getLoadedBlocks(@Nullable Object subLevel, int maximumBlocks) {
        if (!(subLevel instanceof SubLevel sableSubLevel) || !isUsableSubLevel(sableSubLevel, null)) {
            return List.of();
        }
        int limit = Math.max(1, maximumBlocks);
        List<LoadedBlock> blocks = new ArrayList<>();
        try {
            for (PlotChunkHolder holder : sableSubLevel.getPlot().getLoadedChunks()) {
                if (blocks.size() >= limit) break;
                LevelChunk chunk = holder.getChunk();
                if (chunk == null || chunk.isEmpty()) continue;
                chunk.findBlocks(state -> !state.isAir(), (position, state) -> {
                    if (blocks.size() < limit) {
                        blocks.add(new LoadedBlock(position, state));
                    }
                });
            }
        } catch (RuntimeException | LinkageError ignored) {
            // A body may be replaced while its chunk snapshot is being read.
        }
        blocks.sort(Comparator.comparing(LoadedBlock::position));
        return List.copyOf(blocks);
    }

    // Find the actor block entity
    private static @Nullable BlockEntity findActorBlockEntity(LevelPlot plot, BlockPos pos) {
        for (Object actor : plot.getBlockEntityActors()) {
            if (actor instanceof BlockEntity blockEntity && isMatchingBlockEntity(blockEntity, pos)) {
                return blockEntity;
            }
        }
        return null;
    }

    // Check if the block entity is matching
    private static boolean isMatchingBlockEntity(@Nullable BlockEntity blockEntity, BlockPos pos) {
        return blockEntity != null && !blockEntity.isRemoved() && pos.equals(blockEntity.getBlockPos());
    }

    // Check if the sublevel is usable
    private static boolean isUsableSubLevel(@Nullable Object subLevel, @Nullable BlockPos targetPos) {
        if (!(subLevel instanceof SubLevel sableSubLevel)) {
            return false;
        }

        try {
            if (sableSubLevel.isRemoved()) {
                return false;
            }
            LevelPlot plot = sableSubLevel.getPlot();
            if (plot == null) {
                return false;
            }
            if (targetPos == null) {
                return !plot.getLoadedChunks().isEmpty();
            }
            ChunkPos localChunk = plot.toLocal(new ChunkPos(targetPos));
            return plot.getChunkHolder(localChunk) != null;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    // Request the sublevel load
    private static void requestSubLevelLoad(Level level, UUID subLevelId) {
        ServerLevel serverLevel = resolveServerLevel(level);
        if (serverLevel == null) {
            return;
        }

        processPendingSubLevelLoads(serverLevel);
        if (isUsableSubLevel(getSubLevel(serverLevel, subLevelId), null)) {
            return;
        }

        reqChunkForTrackingPoint(serverLevel, subLevelId);
        processPendingSubLevelLoads(serverLevel);
    }

    // Process the pending sublevel loads
    private static void processPendingSubLevelLoads(Level level) {
        ServerLevel serverLevel = resolveServerLevel(level);
        if (serverLevel == null) {
            return;
        }

        SubLevelHoldingChunkMap holdingMap = getHoldingChunkMap(serverLevel);
        if (holdingMap == null) {
            return;
        }

        try {
            holdingMap.processChanges();
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    // Get the holding chunk map
    private static @Nullable SubLevelHoldingChunkMap getHoldingChunkMap(ServerLevel level) {
        ServerSubLevelContainer container;
        try {
            container = SubLevelContainer.getContainer(level);
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
        if (container == null) {
            return null;
        }

        try {
            return container.getHoldingChunkMap();
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    // Request the chunk for tracking point
    private static void reqChunkForTrackingPoint(ServerLevel level, UUID subLevelId) {
        try {
            SubLevelTrackingPointSavedData trackingData = SubLevelTrackingPointSavedData.getOrLoad(level);
            for (Map.Entry<UUID, TrackingPoint> entry : trackingData.getAllTrackingPoints()) {
                TrackingPoint trackingPoint = entry.getValue();
                if (trackingPoint == null || !trackingPoint.inSubLevel()
                        || !subLevelId.equals(trackingPoint.subLevelID())) {
                    continue;
                }
                if (loadTrackingPointChunk(level, subLevelId, trackingPoint)) {
                    return;
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    // Load the tracking point chunk
    private static boolean loadTrackingPointChunk(
            ServerLevel level, UUID subLevelId, TrackingPoint trackingPoint
    ) {
        ChunkPos chunkPos = chunkPosFromPlaceholder(trackingPoint);
        if (chunkPos == null) {
            chunkPos = chunkPosFromSavedPointer(trackingPoint);
        }
        if (chunkPos == null) {
            return false;
        }

        try {
            level.getChunkSource().addRegionTicket(
                    SUB_LEVEL_LOAD_TICKET, chunkPos, 0, subLevelId, true);
            level.getChunk(chunkPos.x, chunkPos.z);
            return true;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        } finally {
            try {
                level.getChunkSource().removeRegionTicket(
                        SUB_LEVEL_LOAD_TICKET, chunkPos, 0, subLevelId, true);
            } catch (RuntimeException | LinkageError ignored) {
            }
        }
    }

    // Get the chunk pos from placeholder
    private static @Nullable ChunkPos chunkPosFromPlaceholder(TrackingPoint trackingPoint) {
        var placeholder = trackingPoint.globalPlaceholderPosition();
        if (placeholder == null) {
            return null;
        }
        return new ChunkPos(BlockPos.containing(placeholder.x(), 0.0D, placeholder.z()));
    }

    // Get the chunk pos from saved pointer
    private static @Nullable ChunkPos chunkPosFromSavedPointer(TrackingPoint trackingPoint) {
        var pointer = trackingPoint.lastSavedSubLevelPointer();
        return pointer == null ? null : pointer.chunkPos();
    }

    // Collect the actor block entities
    private static void collectActorBlockEntities(LevelPlot plot, Map<BlockPos, BlockEntity> blockEntities) {
        for (Object actor : plot.getBlockEntityActors()) {
            if (actor instanceof BlockEntity blockEntity) {
                blockEntities.putIfAbsent(blockEntity.getBlockPos().immutable(), blockEntity);
            }
        }
    }

    // Get the loaded world block entities
    public static List<BlockEntity> getLoadedWorldBlockEntities(@Nullable Level level, BlockPos center, int chunkRadius) {
        List<BlockEntity> blockEntities = new ArrayList<>();
        if (level == null || center == null) {
            return blockEntities;
        }

        int centerChunkX = center.getX() >> 4;
        int centerChunkZ = center.getZ() >> 4;
        for (int chunkX = centerChunkX - chunkRadius; chunkX <= centerChunkX + chunkRadius; chunkX++) {
            for (int chunkZ = centerChunkZ - chunkRadius; chunkZ <= centerChunkZ + chunkRadius; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) {
                    continue;
                }

                blockEntities.addAll(level.getChunk(chunkX, chunkZ).getBlockEntities().values());
            }
        }
        return blockEntities;
    }
}
