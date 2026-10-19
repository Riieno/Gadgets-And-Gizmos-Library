package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

// Read a loaded Sable body's world orientation without forcing it to load
public final class SableSubLevelOrientationApi {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                         PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the sable sub level orientation API
    private SableSubLevelOrientationApi() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Read a loaded sub-level using its stable Sable id
    public static Snapshot sample(@Nullable ServerLevel level, @Nullable UUID subLevelId) {
        if (level == null || subLevelId == null) return Snapshot.unavailable(subLevelId);
        try {
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null
                    || !(container.getSubLevel(subLevelId) instanceof ServerSubLevel subLevel)) {
                return Snapshot.unavailable(subLevelId);
            }
            return sample(subLevel);
        } catch (RuntimeException | LinkageError err) {
            return Snapshot.unavailable(subLevelId);
        }
    }

    // Read an already resolved server sub-level
    public static Snapshot sample(@Nullable ServerSubLevel subLevel) {
        UUID subLevelId = subLevel == null ? null : subLevel.getUniqueId();
        if (subLevel == null || subLevel.isRemoved()) return Snapshot.unavailable(subLevelId);
        try {
            Vector3d forward = subLevel.logicalPose().orientation().transform(
                    new Vector3d(0.0D, 0.0D, -1.0D));
            Vector3d up = subLevel.logicalPose().orientation().transform(
                    new Vector3d(0.0D, 1.0D, 0.0D));
            if (!finite(forward) || !finite(up)) return Snapshot.unavailable(subLevelId);
            return new Snapshot(subLevelId, true,
                    new Vec3(forward.x, forward.y, forward.z),
                    new Vec3(up.x, up.y, up.z));
        } catch (RuntimeException | LinkageError err) {
            return Snapshot.unavailable(subLevelId);
        }
    }

    // Check whether a vector is finite
    private static boolean finite(Vector3d vector) {
        return vector != null && Double.isFinite(vector.x)
                && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    // Normalize one world direction
    private static Vec3 direction(Vec3 vector, Vec3 fallback) {
        if (vector == null || !Double.isFinite(vector.x)
                || !Double.isFinite(vector.y) || !Double.isFinite(vector.z)
                || vector.lengthSqr() <= 1.0E-12D) return fallback;
        return vector.normalize();
    }

    // Store a safe world-space orientation snapshot
    public record Snapshot(@Nullable UUID subLevelId, boolean loaded,
                           Vec3 forward, Vec3 up) {
        // Initialize the orientation snapshot
        public Snapshot {
            forward = direction(forward, new Vec3(0.0D, 0.0D, -1.0D));
            up = direction(up, new Vec3(0.0D, 1.0D, 0.0D));
        }

        // Check whether the orientation is available
        public boolean orientationAvailable() {
            return loaded;
        }

        // Create an unavailable snapshot
        private static Snapshot unavailable(@Nullable UUID subLevelId) {
            return new Snapshot(subLevelId, false, Vec3.ZERO, Vec3.ZERO);
        }
    }
}
