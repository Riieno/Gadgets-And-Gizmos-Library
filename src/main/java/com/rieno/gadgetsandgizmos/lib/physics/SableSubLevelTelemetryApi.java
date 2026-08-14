package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

// Read loaded Sable physics without forcing the body to load
public final class SableSubLevelTelemetryApi {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the sable sub level telemetry API
    private SableSubLevelTelemetryApi() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Read a loaded sub-level using its stable Sable id
    public static Snapshot sample(@Nullable ServerLevel level, @Nullable UUID subLevelId) {
        if (level == null || subLevelId == null) {
            return Snapshot.unavailable(subLevelId);
        }
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

    // Find the loaded bodies connected to a root and remove duplicate ids from the result
    public static Set<UUID> connectedSubLevelIds(@Nullable ServerLevel level,
                                                  @Nullable UUID rootSubLevelId) {
        if (level == null || rootSubLevelId == null) {
            return Set.of();
        }
        try {
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null
                    || !(container.getSubLevel(rootSubLevelId) instanceof ServerSubLevel root)
                    || root.isRemoved()) {
                return Set.of();
            }
            Set<UUID> connected = new LinkedHashSet<>();
            connected.add(rootSubLevelId);
            for (SubLevel candidate : SubLevelHelper.getConnectedChain(root)) {
                if (candidate instanceof ServerSubLevel serverSubLevel
                        && !serverSubLevel.isRemoved()
                        && serverSubLevel.getLevel() == level) {
                    connected.add(serverSubLevel.getUniqueId());
                }
            }
            return Set.copyOf(connected);
        } catch (RuntimeException | LinkageError err) {
            return Set.of();
        }
    }

    // Read an already resolved server sub-level
    public static Snapshot sample(@Nullable ServerSubLevel subLevel) {
        UUID subLevelId = subLevel == null ? null : subLevel.getUniqueId();
        if (subLevel == null || subLevel.isRemoved()) {
            return Snapshot.unavailable(subLevelId);
        }
        try {
            Vec3 pos = vec3(subLevel.logicalPose().position());
            double mass = mass(subLevel.getMassTracker());
            try {
                RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
                if (handle == null || !handle.isValid()) {
                    return new Snapshot(subLevelId, true, false,
                            pos, Vec3.ZERO, Vec3.ZERO, mass);
                }
                Vector3d linear = handle.getLinearVelocity(new Vector3d());
                Vector3d angular = handle.getAngularVelocity(new Vector3d());
                if (isFinite(linear) && isFinite(angular)) {
                    return new Snapshot(subLevelId, true, true, pos,
                            vec3(linear), vec3(angular), mass);
                }
            } catch (RuntimeException | LinkageError ignored) {
            }
            return new Snapshot(subLevelId, true, false,
                    pos, Vec3.ZERO, Vec3.ZERO, mass);
        } catch (RuntimeException | LinkageError err) {
            return Snapshot.unavailable(subLevelId);
        }
    }

    // Get the mass
    private static double mass(@Nullable MassData massData) {
        if (massData == null || massData.isInvalid()) {
            return 0.0D;
        }
        double mass = massData.getMass();
        return Double.isFinite(mass) && mass > 0.0D ? mass : 0.0D;
    }

    // Convert the vector to a Minecraft position
    private static Vec3 vec3(@Nullable Vector3dc vector) {
        return isFinite(vector) ? new Vec3(vector.x(), vector.y(), vector.z()) : Vec3.ZERO;
    }

    // Normalize the vector to finite values
    private static Vec3 finiteVec3(@Nullable Vec3 vector) {
        return vector != null
                && Double.isFinite(vector.x)
                && Double.isFinite(vector.y)
                && Double.isFinite(vector.z)
                ? vector : Vec3.ZERO;
    }

    // Check if this is finite
    private static boolean isFinite(@Nullable Vector3dc vector) {
        return vector != null
                && Double.isFinite(vector.x())
                && Double.isFinite(vector.y())
                && Double.isFinite(vector.z());
    }

    // Store a safe finite snapshot of one Sable rigid body
    public record Snapshot(@Nullable UUID subLevelId, boolean loaded, boolean physicsAvailable,
                           Vec3 position, Vec3 linearVelocity, Vec3 angularVelocity,
                           double mass) {
        // Initialize the snapshot
        public Snapshot {
            position = finiteVec3(position);
            linearVelocity = finiteVec3(linearVelocity);
            angularVelocity = finiteVec3(angularVelocity);
            mass = Double.isFinite(mass) && mass > 0.0D ? mass : 0.0D;
        }

        // Get the current world speed
        public double speed() {
            double speed = linearVelocity.length();
            return Double.isFinite(speed) ? speed : 0.0D;
        }

        // Check whether Sable supplied a usable mass
        public boolean massAvailable() {
            return mass > 0.0D;
        }

        // Create an unavailable snapshot
        private static Snapshot unavailable(@Nullable UUID subLevelId) {
            return new Snapshot(subLevelId, false, false,
                    Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 0.0D);
        }
    }
}
