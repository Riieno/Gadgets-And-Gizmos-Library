package com.rieno.gadgetsandgizmos.lib.probe;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

// Find and resolve block entities across root levels and loaded Sable SubLevels
public final class BlockEntityLookupApi {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Store one resolved block position and owning SubLevel
    public record ResolvedBlockPosition(BlockPos blockPos, @Nullable UUID subLevelId) {
        // Normalize the resolved block position
        public ResolvedBlockPosition {
            blockPos = blockPos == null ? BlockPos.ZERO : blockPos.immutable();
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the block entity lookup API
    private BlockEntityLookupApi() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Find one block entity through nested SubLevels
    public static @Nullable BlockEntity findIncludingSubLevels(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return null;
        }
        SubLevel containing = SableLevelApi.containing(level, pos);
        return Sable.HELPER.runIncludingSubLevels(level, pos.getCenter(), true, containing,
                (subLevel, internalPos) -> findInAccess(level, subLevel, internalPos));
    }

    // Find one typed block entity through nested SubLevels
    public static <T extends BlockEntity> @Nullable T findIncludingSubLevels(
            Level level, BlockPos pos, Class<T> type) {
        BlockEntity blockEntity = findIncludingSubLevels(level, pos);
        return type != null && type.isInstance(blockEntity) ? type.cast(blockEntity) : null;
    }

    // Resolve one block position through nested SubLevels
    public static ResolvedBlockPosition resolveIncludingSubLevels(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return new ResolvedBlockPosition(BlockPos.ZERO, null);
        }
        SubLevel containing = SableLevelApi.containing(level, pos);
        ResolvedBlockPosition resolved = Sable.HELPER.runIncludingSubLevels(
                level, pos.getCenter(), true, containing,
                (subLevel, internalPos) -> resolveInAccess(level, subLevel, internalPos));
        return resolved == null ? new ResolvedBlockPosition(pos, null) : resolved;
    }

    // Find one block entity by optional SubLevel ID
    public static @Nullable BlockEntity find(Level level, @Nullable UUID subLevelId, BlockPos pos) {
        if (level == null || pos == null) {
            return null;
        }
        if (subLevelId == null) {
            return SubLevelBlockEntityCollector.ensureTargetLoaded(level, null, pos)
                    ? level.getBlockEntity(pos)
                    : null;
        }

        if (!SubLevelBlockEntityCollector.ensureTargetLoaded(level, subLevelId, pos)) {
            return SubLevelBlockEntityCollector.isSubLevelPlotPosition(level, pos)
                    ? null
                    : level.getBlockEntity(pos);
        }
        SubLevel subLevel = SableLevelApi.subLevel(level, subLevelId);
        if (subLevel == null) {
            return SubLevelBlockEntityCollector.isSubLevelPlotPosition(level, pos)
                    ? null
                    : level.getBlockEntity(pos);
        }
        BlockEntity blockEntity = SubLevelBlockEntityCollector.getBlockEntity(subLevel, pos);
        return blockEntity == null ? level.getBlockEntity(pos) : blockEntity;
    }

    // Find one typed block entity by optional SubLevel ID
    public static <T extends BlockEntity> @Nullable T find(
            Level level, @Nullable UUID subLevelId, BlockPos pos, Class<T> type) {
        BlockEntity blockEntity = find(level, subLevelId, pos);
        return type != null && type.isInstance(blockEntity) ? type.cast(blockEntity) : null;
    }

    // Find one block entity only in the requested level scope
    public static @Nullable BlockEntity findExact(Level level, @Nullable UUID subLevelId, BlockPos pos) {
        if (level == null || pos == null) {
            return null;
        }
        if (subLevelId == null) {
            return SubLevelBlockEntityCollector.isSubLevelPlotPosition(level, pos)
                    ? null
                    : level.getBlockEntity(pos);
        }
        if (!SubLevelBlockEntityCollector.ensureTargetLoaded(level, subLevelId, pos)) {
            return null;
        }
        return SubLevelBlockEntityCollector.getBlockEntity(SableLevelApi.subLevel(level, subLevelId), pos);
    }

    // Find one already-loaded block entity in the requested level scope
    public static @Nullable BlockEntity findLoadedExact(Level level, @Nullable UUID subLevelId, BlockPos pos) {
        if (level == null || pos == null) {
            return null;
        }
        if (subLevelId == null) {
            return SubLevelBlockEntityCollector.isSubLevelPlotPosition(level, pos) || !level.isLoaded(pos)
                    ? null
                    : level.getBlockEntity(pos);
        }
        return SubLevelBlockEntityCollector.getBlockEntity(SableLevelApi.subLevel(level, subLevelId), pos);
    }

    // Find one typed block entity inside a SubLevel
    public static <T extends BlockEntity> @Nullable T findInSubLevel(
            @Nullable SubLevel subLevel, BlockPos pos, Class<T> type) {
        if (subLevel == null || pos == null || type == null) {
            return null;
        }
        BlockEntity blockEntity = SubLevelBlockEntityCollector.getBlockEntity(subLevel, pos);
        return type.isInstance(blockEntity) ? type.cast(blockEntity) : null;
    }

    // Find one block entity in the current lookup scope
    private static @Nullable BlockEntity findInAccess(
            Level level, @Nullable SubLevel subLevel, BlockPos pos) {
        return subLevel == null
                ? level.getBlockEntity(pos)
                : SubLevelBlockEntityCollector.getBlockEntity(subLevel, pos);
    }

    // Resolve one block position in the current lookup scope
    private static @Nullable ResolvedBlockPosition resolveInAccess(
            Level level, @Nullable SubLevel subLevel, BlockPos pos) {
        UUID subLevelId = subLevel == null ? null : subLevel.getUniqueId();
        if (!SubLevelBlockEntityCollector.ensureTargetLoaded(level, subLevelId, pos)) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        return state.isAir() ? null : new ResolvedBlockPosition(pos, subLevelId);
    }
}
