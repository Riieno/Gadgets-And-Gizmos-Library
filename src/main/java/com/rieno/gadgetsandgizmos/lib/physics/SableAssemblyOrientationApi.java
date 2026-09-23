package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

// Sample a mass-weighted common orientation for one loaded Sable assembly
public final class SableAssemblyOrientationApi {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                         PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the sable assembly orientation API
    private SableAssemblyOrientationApi() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Sample the common orientation and angular velocity of every loaded body
    public static Snapshot sample(@Nullable SableAssemblyTopologyApi.Topology topology) {
        UUID rootId = topology == null ? null : topology.rootSubLevelId();
        if (topology == null || !topology.available() || topology.bodies().isEmpty()) {
            return Snapshot.unavailable(rootId);
        }
        Vec3 weightedForward = Vec3.ZERO;
        Vec3 weightedUp = Vec3.ZERO;
        Vec3 weightedAngularVelocity = Vec3.ZERO;
        double totalMass = 0.0D;
        for (SableAssemblyTopologyApi.Body body : topology.bodies()) {
            ServerSubLevel subLevel = body == null ? null : body.subLevel();
            if (subLevel == null || subLevel.isRemoved()) continue;
            SableSubLevelOrientationApi.Snapshot orientation =
                    SableSubLevelOrientationApi.sample(subLevel);
            if (!orientation.orientationAvailable()) continue;
            SableSubLevelTelemetryApi.Snapshot telemetry =
                    SableSubLevelTelemetryApi.sample(subLevel);
            double mass = telemetry.massAvailable() ? telemetry.mass() : 1.0D;
            weightedForward = weightedForward.add(orientation.forward().scale(mass));
            weightedUp = weightedUp.add(orientation.up().scale(mass));
            weightedAngularVelocity = weightedAngularVelocity.add(
                    telemetry.angularVelocity().scale(mass));
            totalMass += mass;
        }
        if (!Double.isFinite(totalMass) || totalMass <= 1.0E-9D) {
            return Snapshot.unavailable(rootId);
        }
        Vec3 up = direction(weightedUp, new Vec3(0.0D, 1.0D, 0.0D));
        Vec3 forward = weightedForward.subtract(up.scale(weightedForward.dot(up)));
        forward = direction(forward, perpendicular(up));
        return new Snapshot(rootId, true, totalMass, forward, up,
                weightedAngularVelocity.scale(1.0D / totalMass));
    }

    // Normalize one direction
    private static Vec3 direction(Vec3 vector, Vec3 fallback) {
        if (vector == null || !Double.isFinite(vector.x)
                || !Double.isFinite(vector.y) || !Double.isFinite(vector.z)
                || vector.lengthSqr() <= 1.0E-12D) return fallback;
        return vector.normalize();
    }

    // Get one stable direction orthogonal to the supplied up vector
    private static Vec3 perpendicular(Vec3 up) {
        Vec3 basis = Math.abs(up.y) < 0.9D
                ? new Vec3(0.0D, 1.0D, 0.0D) : new Vec3(0.0D, 0.0D, -1.0D);
        return direction(basis.cross(up), new Vec3(0.0D, 0.0D, -1.0D));
    }

    // Store safe common-body orientation telemetry
    public record Snapshot(@Nullable UUID rootSubLevelId, boolean available, double mass,
                           Vec3 forward, Vec3 up, Vec3 angularVelocity) {
        // Initialize the assembly orientation snapshot
        public Snapshot {
            mass = Double.isFinite(mass) && mass > 0.0D ? mass : 0.0D;
            forward = direction(forward, new Vec3(0.0D, 0.0D, -1.0D));
            up = direction(up, new Vec3(0.0D, 1.0D, 0.0D));
            angularVelocity = angularVelocity == null
                    || !Double.isFinite(angularVelocity.x)
                    || !Double.isFinite(angularVelocity.y)
                    || !Double.isFinite(angularVelocity.z)
                    ? Vec3.ZERO : angularVelocity;
        }

        // Create an unavailable snapshot
        private static Snapshot unavailable(@Nullable UUID rootSubLevelId) {
            return new Snapshot(rootSubLevelId, false, 0.0D,
                    Vec3.ZERO, Vec3.ZERO, Vec3.ZERO);
        }
    }
}
