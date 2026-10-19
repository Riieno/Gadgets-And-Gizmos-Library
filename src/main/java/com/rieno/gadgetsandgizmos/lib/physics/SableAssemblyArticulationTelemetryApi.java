package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Sample the live relative pose and angular rate at carriage-coupler connections
public final class SableAssemblyArticulationTelemetryApi {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the Sable assembly articulation telemetry API
    private SableAssemblyArticulationTelemetryApi() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Sample every loaded carriage-coupler connection in the topology root frame
    public static Snapshot sample(@Nullable SableAssemblyTopologyApi.Topology topology) {
        UUID rootId = topology == null ? null : topology.rootSubLevelId();
        if (topology == null || !topology.available() || rootId == null) {
            return Snapshot.unavailable(rootId);
        }
        SableAssemblyTopologyApi.Body rootBody = topology.body(rootId).orElse(null);
        if (rootBody == null || rootBody.subLevel() == null || rootBody.subLevel().isRemoved()) {
            return Snapshot.unavailable(rootId);
        }
        try {
            ServerSubLevel root = rootBody.subLevel();
            List<Joint> joints = new ArrayList<>();
            for (SableAssemblyTopologyApi.Edge edge : topology.edges()) {
                if (edge.kind() != SableAssemblyConnection.Kind.CARRIAGE_COUPLER) continue;
                SableAssemblyTopologyApi.Body first = topology.body(edge.firstSubLevelId()).orElse(null);
                SableAssemblyTopologyApi.Body second = topology.body(edge.secondSubLevelId()).orElse(null);
                if (first == null || second == null) continue;
                Joint joint = sample(root, first.subLevel(), second.subLevel());
                if (joint != null) joints.add(joint);
            }
            return new Snapshot(rootId, true, joints);
        } catch (RuntimeException | LinkageError err) {
            return Snapshot.unavailable(rootId);
        }
    }

    // Sample one articulation in the root-local reference frame
    private static @Nullable Joint sample(
            ServerSubLevel root,
            @Nullable ServerSubLevel first,
            @Nullable ServerSubLevel second
    ) {
        if (first == null || second == null || first.isRemoved() || second.isRemoved()) return null;
        UUID firstId = first.getUniqueId();
        UUID secondId = second.getUniqueId();
        if (firstId == null || secondId == null) return null;
        Vec3 firstForward = rootDirection(root, first, new Vector3d(0.0D, 0.0D, -1.0D));
        Vec3 firstUp = rootDirection(root, first, new Vector3d(0.0D, 1.0D, 0.0D));
        Vec3 secondForward = rootDirection(root, second, new Vector3d(0.0D, 0.0D, -1.0D));
        Vec3 secondUp = rootDirection(root, second, new Vector3d(0.0D, 1.0D, 0.0D));
        SableSubLevelTelemetryApi.Snapshot firstTelemetry = SableSubLevelTelemetryApi.sample(first);
        SableSubLevelTelemetryApi.Snapshot secondTelemetry = SableSubLevelTelemetryApi.sample(second);
        Vec3 relativeAngularVelocity = rootDirection(root,
                firstTelemetry.angularVelocity().subtract(secondTelemetry.angularVelocity()));
        return new Joint(firstId, secondId, firstForward, firstUp,
                secondForward, secondUp, relativeAngularVelocity,
                firstTelemetry.physicsAvailable() && secondTelemetry.physicsAvailable());
    }

    // Transform a world direction into the topology root frame
    private static Vec3 rootDirection(ServerSubLevel root, @Nullable Vec3 worldDirection) {
        if (!finite(worldDirection)) return Vec3.ZERO;
        try {
            Vector3d direction = new Vector3d(
                    worldDirection.x, worldDirection.y, worldDirection.z);
            root.logicalPose().orientation().transformInverse(direction);
            return finite(direction) ? new Vec3(direction.x, direction.y, direction.z) : Vec3.ZERO;
        } catch (RuntimeException | LinkageError err) {
            return Vec3.ZERO;
        }
    }

    // Transform one body-local direction into the topology root frame
    private static Vec3 rootDirection(ServerSubLevel root, ServerSubLevel body, Vector3d direction) {
        try {
            body.logicalPose().orientation().transform(direction);
            root.logicalPose().orientation().transformInverse(direction);
            return finite(direction) ? new Vec3(direction.x, direction.y, direction.z) : Vec3.ZERO;
        } catch (RuntimeException | LinkageError err) {
            return Vec3.ZERO;
        }
    }

    // Check whether a vector is finite
    private static boolean finite(@Nullable Vec3 value) {
        return value != null && Double.isFinite(value.x)
                && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    // Check whether a vector is finite
    private static boolean finite(@Nullable Vector3d value) {
        return value != null && Double.isFinite(value.x)
                && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    // Store live articulation telemetry in one stable root-local frame
    public record Snapshot(@Nullable UUID rootSubLevelId, boolean available, List<Joint> joints) {
        // Initialize the snapshot
        public Snapshot {
            joints = joints == null ? List.of() : List.copyOf(joints);
        }

        // Create unavailable articulation telemetry
        private static Snapshot unavailable(@Nullable UUID rootSubLevelId) {
            return new Snapshot(rootSubLevelId, false, List.of());
        }
    }

    // Store one carriage-coupler pair's live pose and relative angular rate
    public record Joint(
            UUID firstSubLevelId,
            UUID secondSubLevelId,
            Vec3 firstForward,
            Vec3 firstUp,
            Vec3 secondForward,
            Vec3 secondUp,
            Vec3 relativeAngularVelocity,
            boolean physicsAvailable
    ) {
        // Initialize the joint telemetry
        public Joint {
            firstForward = finite(firstForward) ? firstForward : Vec3.ZERO;
            firstUp = finite(firstUp) ? firstUp : Vec3.ZERO;
            secondForward = finite(secondForward) ? secondForward : Vec3.ZERO;
            secondUp = finite(secondUp) ? secondUp : Vec3.ZERO;
            relativeAngularVelocity = finite(relativeAngularVelocity)
                    ? relativeAngularVelocity : Vec3.ZERO;
        }

        // Check whether this joint touches the requested body
        public boolean touches(@Nullable UUID subLevelId) {
            return subLevelId != null && (subLevelId.equals(firstSubLevelId)
                    || subLevelId.equals(secondSubLevelId));
        }

        // Get the angular velocity correction required for the requested body
        public Vec3 relativeAngularVelocityFor(@Nullable UUID subLevelId) {
            if (subLevelId == null || subLevelId.equals(firstSubLevelId)) {
                return relativeAngularVelocity;
            }
            return subLevelId.equals(secondSubLevelId)
                    ? relativeAngularVelocity.scale(-1.0D) : Vec3.ZERO;
        }
    }
}
