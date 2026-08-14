package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

// Resolve root levels and typed Sable ownership without reflection
public final class SableLevelApi {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the Sable level API
    private SableLevelApi() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Resolve the root server level
    public static @Nullable ServerLevel serverLevel(@Nullable Level level) {
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel;
        }
        if (level == null || level.getServer() == null) {
            return null;
        }
        ServerLevel byDimension = level.getServer().getLevel(level.dimension());
        return byDimension == null ? level.getServer().overworld() : byDimension;
    }

    // Resolve the root server level for one sublevel
    public static @Nullable ServerLevel serverLevel(@Nullable SubLevel subLevel) {
        if (subLevel instanceof ServerSubLevel serverSubLevel) {
            return serverSubLevel.getLevel();
        }
        return subLevel == null ? null : serverLevel(subLevel.getLevel());
    }

    // Get the sublevel containing one block entity
    public static @Nullable SubLevel containing(@Nullable BlockEntity blockEntity) {
        return blockEntity == null ? null : Sable.HELPER.getContaining(blockEntity);
    }

    // Get the sublevel containing one entity
    public static @Nullable SubLevel containing(@Nullable Entity entity) {
        return entity == null ? null : Sable.HELPER.getContaining(entity);
    }

    // Get the SubLevel tracking or carrying one entity
    public static @Nullable SubLevel tracking(@Nullable Entity entity) {
        return entity == null ? null : Sable.HELPER.getTrackingOrVehicleSubLevel(entity);
    }

    // Get the sublevel containing one position
    public static @Nullable SubLevel containing(@Nullable Level level, @Nullable BlockPos pos) {
        return level == null || pos == null ? null : Sable.HELPER.getContaining(level, pos);
    }

    // Get the sublevel containing one precise position
    public static @Nullable SubLevel containing(@Nullable Level level, @Nullable Position pos) {
        return level == null || pos == null ? null : Sable.HELPER.getContaining(level, pos);
    }

    // Get the containing sublevel ID
    public static @Nullable UUID containingId(@Nullable BlockEntity blockEntity) {
        SubLevel subLevel = containing(blockEntity);
        return subLevel == null ? null : subLevel.getUniqueId();
    }

    // Get the sublevel ID containing one precise position
    public static @Nullable UUID containingId(@Nullable Level level, @Nullable Position pos) {
        SubLevel subLevel = containing(level, pos);
        return subLevel == null ? null : subLevel.getUniqueId();
    }

    // Resolve one live sublevel
    public static @Nullable SubLevel subLevel(@Nullable Level level, @Nullable UUID subLevelId) {
        return SubLevelConnectionApi.resolve(level, subLevelId);
    }

    // Get every live sublevel in one container
    public static List<? extends SubLevel> subLevels(@Nullable Level level) {
        if (level == null) {
            return List.of();
        }
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        return container == null ? List.of() : List.copyOf(container.getAllSubLevels());
    }
}
