package com.rieno.gadgetsandgizmos.lib.kinetics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.state.BlockState;

// Keep exact kinetic angles normalized and safe across Create propagation
public final class KineticAngleHelper {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the kinetic angle
    private KineticAngleHelper() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Clear the held angles
    public static boolean clearHeldAngles(KineticBlockEntity target) {
        if (!GadgetsNGizmosKineticGuard.shouldApplyHeldAngleTo(target)) {
            return false;
        }
        if (!(target instanceof HeldKineticAngleAccess access)) {
            return false;
        }
        return access.ct$clearHeldAngles();
    }

    // Publish the rotation angle
    public static boolean publishRotationAngle(KineticBlockEntity target, float angleDegrees) {
        if (!GadgetsNGizmosKineticGuard.shouldApplyHeldAngleTo(target)) {
            return false;
        }
        BlockState state = target.getBlockState();
        if (!(state.getBlock() instanceof IRotate rotate) || !(target instanceof HeldKineticAngleAccess access)) {
            return false;
        }
        return access.ct$setHeldAngle(rotate.getRotationAxis(state), angleDegrees);
    }

    // Get the published rotation angle degrees
    public static double getPublishedRotationAngleDegrees(KineticBlockEntity target, Direction.Axis axis) {
        return getHeldRotationAngleDegrees(target, axis);
    }

    // Get the held rotation angle degrees
    public static double getHeldRotationAngleDegrees(KineticBlockEntity target, Direction.Axis axis) {
        if (!(target instanceof HeldKineticAngleAccess access) || !access.ct$hasHeldAngle(axis)) {
            return Double.NaN;
        }
        float absoluteAngle = access.ct$getAbsoluteRotationAngle(axis);
        return Float.isNaN(absoluteAngle) ? Double.NaN : absoluteAngle;
    }

    // Get the absolute rotation angle degrees
    public static double getAbsoluteRotationAngleDegrees(KineticBlockEntity target, Direction.Axis axis) {
        if (!(target instanceof HeldKineticAngleAccess access)) {
            return Double.NaN;
        }
        float absoluteAngle = access.ct$getAbsoluteRotationAngle(axis);
        return Float.isNaN(absoluteAngle) ? Double.NaN : absoluteAngle;
    }

    // Get the kinetic rotation angle degrees
    public static float getKineticRotationAngleDegrees(KineticBlockEntity target) {
        if (target.getLevel() == null) {
            return 0.0f;
        }
        BlockState state = target.getBlockState();
        if (!(state.getBlock() instanceof IRotate rotate)) {
            return 0.0f;
        }
        Direction.Axis axis = rotate.getRotationAxis(state);
        float offset = getRotationOffsetForPos(target, state, axis);
        return normalizeDegrees(target.getLevel().getGameTime() * target.getSpeed() * 3.0f / 10.0f + offset);
    }

    // Get the rotation offset for pos
    private static float getRotationOffsetForPos(KineticBlockEntity target, BlockState state, Direction.Axis axis) {
        return getRotationOffset(state, axis, target.getBlockPos()) + target.getRotationAngleOffset(axis);
    }

    // Get the rotation offset
    private static float getRotationOffset(BlockState state, Direction.Axis axis, Vec3i pos) {
        if (shouldOffset(axis, pos)) {
            return 22.5f;
        }
        return ICogWheel.isLargeCog(state) ? 11.25f : 0.0f;
    }

    // Check if this should offset
    private static boolean shouldOffset(Direction.Axis axis, Vec3i pos) {
        int x = axis == Direction.Axis.X ? 0 : pos.getX();
        int y = axis == Direction.Axis.Y ? 0 : pos.getY();
        int z = axis == Direction.Axis.Z ? 0 : pos.getZ();
        return (x + y + z) % 2 == 0;
    }

    // Normalize the degrees
    public static float normalizeDegrees(float angleDegrees) {
        float normalized = angleDegrees % 360.0f;
        if (normalized <= -180.0f) {
            normalized += 360.0f;
        }
        if (normalized > 180.0f) {
            normalized -= 360.0f;
        }
        return normalized;
    }

    // Normalize the degrees
    public static double normalizeDegrees(double angleDegrees) {
        double normalized = angleDegrees % 360.0D;
        if (normalized <= -180.0D) {
            normalized += 360.0D;
        }
        if (normalized > 180.0D) {
            normalized -= 360.0D;
        }
        return normalized;
    }
}
