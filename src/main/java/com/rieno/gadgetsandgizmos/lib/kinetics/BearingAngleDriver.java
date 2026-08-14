package com.rieno.gadgetsandgizmos.lib.kinetics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.bearing.IBearingBlockEntity;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

// Drive the first compatible downstream Create or Simulated bearing
public final class BearingAngleDriver {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final @Nullable Field SWIVEL_LAST_TARGET_ANGLE_FIELD =
            getSwivelBearingField("lastTargetAngleDegrees");
    private static final @Nullable Field SWIVEL_TARGET_ANGLE_FIELD =
            getSwivelBearingField("targetAngleDegrees");

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the bearing angle driver
    private BearingAngleDriver() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Drive the first downstream bearing
    public static boolean driveFirstDownstreamBearing(Level level, BlockPos origin, Direction direction,
                                                      double angleDegrees, int maxSteps,
                                                      BlockEntitySynchronizer synchronizer) {
        BlockPos cursor = origin.relative(direction);
        for (int steps = 0; steps < maxSteps; steps++, cursor = cursor.relative(direction)) {
            BlockEntity blockEntity = level.getBlockEntity(cursor);
            if (blockEntity == null) {
                return false;
            }

            if (blockEntity instanceof MechanicalBearingBlockEntity mechanicalBearing) {
                applyMechanicalBearingAngle(mechanicalBearing, angleDegrees, synchronizer);
                return true;
            }
            if (blockEntity instanceof SwivelBearingBlockEntity swivelBearing) {
                applySwivelBearingAngle(swivelBearing, angleDegrees, synchronizer);
                return true;
            }
            if (blockEntity instanceof IBearingBlockEntity bearing) {
                bearing.setAngle((float) angleDegrees);
                synchronizer.sync((BlockEntity) bearing);
                return true;
            }
            if (!(blockEntity instanceof KineticBlockEntity)) {
                return false;
            }
        }
        return false;
    }

    // Apply the mechanical bearing angle
    private static void applyMechanicalBearingAngle(MechanicalBearingBlockEntity bearing, double angleDegrees,
                                                    BlockEntitySynchronizer synchronizer) {
        if (!bearing.isRunning()) {
            bearing.assemble();
        }
        bearing.setAngle((float) angleDegrees);
        ControlledContraptionEntity contraption = bearing.getMovedContraption();
        if (contraption != null) {
            contraption.setAngle((float) angleDegrees);
        }
        synchronizer.sync(bearing);
    }

    // Apply the swivel bearing angle
    private static void applySwivelBearingAngle(SwivelBearingBlockEntity bearing, double angleDegrees,
                                                BlockEntitySynchronizer synchronizer) {
        if (SWIVEL_LAST_TARGET_ANGLE_FIELD == null || SWIVEL_TARGET_ANGLE_FIELD == null) {
            return;
        }
        try {
            SWIVEL_LAST_TARGET_ANGLE_FIELD.setDouble(bearing, angleDegrees);
            SWIVEL_TARGET_ANGLE_FIELD.setDouble(bearing, angleDegrees);
        } catch (IllegalAccessException ignored) {
            return;
        }
        synchronizer.sync(bearing);
    }

    // Get the swivel bearing field
    @Nullable
    private static Field getSwivelBearingField(String name) {
        try {
            Field field = SwivelBearingBlockEntity.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    // Expose the block entity synchronizer
    @FunctionalInterface
    public interface BlockEntitySynchronizer {
        // Sync the block entity synchronizer
        void sync(BlockEntity blockEntity);
    }
}
