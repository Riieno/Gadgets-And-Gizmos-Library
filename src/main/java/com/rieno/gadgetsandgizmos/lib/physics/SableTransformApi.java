package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// Transform positions and directions through live Sable SubLevels
public final class SableTransformApi {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the Sable transform API
    private SableTransformApi() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Convert one world direction to SubLevel space
    public static Vec3 toLocalDirection(@Nullable SubLevel subLevel, Vec3 dir) {
        if (dir == null || dir.lengthSqr() < 1.0E-6D) {
            return dir;
        }
        return subLevel == null ? dir.normalize() : subLevel.logicalPose().transformNormalInverse(dir).normalize();
    }

    // Convert one local direction to world space
    public static Vec3 toWorldDirection(@Nullable SubLevel subLevel, Vec3 dir) {
        if (dir == null || dir.lengthSqr() < 1.0E-6D) {
            return dir;
        }
        return subLevel == null ? dir.normalize() : subLevel.logicalPose().transformNormal(dir).normalize();
    }

    // Convert one block direction to SubLevel space
    public static Vec3 toLocalDirection(BlockEntity blockEntity, Vec3 dir) {
        return toLocalDirection(SableLevelApi.containing(blockEntity), dir);
    }

    // Convert one block direction to world space
    public static Vec3 toWorldDirection(BlockEntity blockEntity, Vec3 dir) {
        return toWorldDirection(SableLevelApi.containing(blockEntity), dir);
    }

    // Convert one world position to SubLevel space
    public static @Nullable Vec3 toLocalPosition(@Nullable SubLevel subLevel, @Nullable Vec3 pos) {
        return subLevel == null || pos == null ? pos : subLevel.logicalPose().transformPositionInverse(pos);
    }

    // Convert one local position to world space
    public static @Nullable Vec3 toWorldPosition(@Nullable SubLevel subLevel, @Nullable Vec3 pos) {
        return subLevel == null || pos == null ? pos : subLevel.logicalPose().transformPosition(pos);
    }

    // Convert one block position to SubLevel space
    public static @Nullable Vec3 toLocalPosition(BlockEntity blockEntity, @Nullable Vec3 pos) {
        return toLocalPosition(SableLevelApi.containing(blockEntity), pos);
    }

    // Convert one block position to world space
    public static @Nullable Vec3 toWorldPosition(BlockEntity blockEntity, @Nullable Vec3 pos) {
        return toWorldPosition(SableLevelApi.containing(blockEntity), pos);
    }

    // Project one position out through nested SubLevels
    public static @Nullable Vec3 projectOut(Level level, @Nullable Vec3 pos) {
        if (level == null || pos == null) {
            return pos;
        }
        Vec3 projected = pos;
        for (int idx = 0; idx < 8; idx++) {
            Vec3 next = Sable.HELPER.projectOutOfSubLevel(level, projected);
            if (next == null || next.distanceToSqr(projected) <= 1.0E-12D) {
                return projected;
            }
            projected = next;
        }
        return projected;
    }

    // Project one position out through its immediate SubLevel
    public static @Nullable Vec3 projectOutOne(Level level, @Nullable Vec3 pos) {
        return level == null || pos == null ? pos : Sable.HELPER.projectOutOfSubLevel(level, pos);
    }

    // Measure distance through SubLevel transforms
    public static double distanceSquared(Level level, Vec3 from, Vec3 to) {
        if (from == null || to == null) {
            return Double.MAX_VALUE;
        }
        return Sable.HELPER.distanceSquaredWithSubLevels(level, from, to.x, to.y, to.z);
    }

    // Get loaded SubLevels intersecting one world box
    public static List<SubLevel> intersecting(Level level, AABB bounds) {
        if (level == null || bounds == null) {
            return List.of();
        }
        List<SubLevel> subLevels = new ArrayList<>();
        for (SubLevel subLevel : Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(bounds))) {
            if (subLevel != null && !subLevel.isRemoved()) {
                subLevels.add(subLevel);
            }
        }
        return List.copyOf(subLevels);
    }

    // Move one entity into a SubLevel
    public static void kick(SubLevel subLevel, Entity entity) {
        if (subLevel != null && entity != null) {
            EntitySubLevelUtil.kickEntity(subLevel, entity);
        }
    }
}
