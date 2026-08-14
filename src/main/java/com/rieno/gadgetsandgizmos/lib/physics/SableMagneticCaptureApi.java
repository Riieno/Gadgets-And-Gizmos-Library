package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

// Apply a bounded opposing pull between two loaded Sable body anchors
public final class SableMagneticCaptureApi {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final double MINIMUM_DISTANCE_SQUARED = 1.0E-8D;
    private static final double MINIMUM_RADIUS = 1.0E-4D;
    private static final double MINIMUM_ACCELERATION = 1.0E-4D;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the sable magnetic capture API
    private SableMagneticCaptureApi() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Pull the magnetic targets together
    public static boolean pullTogether(
            @Nullable ServerSubLevel first,
            @Nullable Vector3dc firstAnchor,
            @Nullable ServerSubLevel second,
            @Nullable Vector3dc secondAnchor,
            double captureRadius,
            double maximumClosingAcceleration,
            double timeStep
    ) {
        if (!valid(first, firstAnchor) || !valid(second, secondAnchor) || first == second
                || !Double.isFinite(captureRadius) || captureRadius < MINIMUM_RADIUS
                || !Double.isFinite(maximumClosingAcceleration)
                || maximumClosingAcceleration < MINIMUM_ACCELERATION
                || !Double.isFinite(timeStep) || timeStep <= 0.0D) {
            return false;
        }
        try {
            Vector3d firstWorld = first.logicalPose().transformPosition(
                    new Vector3d(firstAnchor), new Vector3d());
            Vector3d secondWorld = second.logicalPose().transformPosition(
                    new Vector3d(secondAnchor), new Vector3d());
            Vector3d worldSeparation = secondWorld.sub(firstWorld, new Vector3d());
            double distanceSquared = worldSeparation.lengthSquared();
            if (!finite(worldSeparation) || distanceSquared < MINIMUM_DISTANCE_SQUARED
                    || distanceSquared > captureRadius * captureRadius) {
                return false;
            }

            RigidBodyHandle firstHandle = RigidBodyHandle.of(first);
            RigidBodyHandle secondHandle = RigidBodyHandle.of(second);
            if (firstHandle == null || secondHandle == null
                    || !firstHandle.isValid() || !secondHandle.isValid()) {
                return false;
            }
            double inverseMass = first.getMassTracker().getInverseMass()
                    + second.getMassTracker().getInverseMass();
            if (!Double.isFinite(inverseMass) || inverseMass <= 1.0E-9D) {
                return false;
            }

            double distance = Math.sqrt(distanceSquared);
            double proximity = 1.0D - distance / captureRadius;
            double acceleration = maximumClosingAcceleration * (0.2D + 0.8D * proximity * proximity);
            double force = acceleration / inverseMass;
            if (!Double.isFinite(force) || force <= 0.0D) {
                return false;
            }

            Vector3d firstDirection = first.logicalPose().orientation()
                    .transformInverse(new Vector3d(worldSeparation));
            Vector3d secondDirection = second.logicalPose().orientation()
                    .transformInverse(new Vector3d(worldSeparation).negate());
            boolean firstApplied = SablePointImpulseApi.applyDirectional(first, firstHandle,
                    ForceGroups.MAGNETIC_FORCE.get(), firstAnchor, firstDirection, force, timeStep);
            boolean secondApplied = SablePointImpulseApi.applyDirectional(second, secondHandle,
                    ForceGroups.MAGNETIC_FORCE.get(), secondAnchor, secondDirection, force, timeStep);
            return firstApplied || secondApplied;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    // Check if this is valid
    private static boolean valid(@Nullable ServerSubLevel subLevel, @Nullable Vector3dc anchor) {
        return subLevel != null && !subLevel.isRemoved() && finite(anchor);
    }

    // Normalize the value to a finite result
    private static boolean finite(@Nullable Vector3dc val) {
        return val != null && Double.isFinite(val.x())
                && Double.isFinite(val.y()) && Double.isFinite(val.z());
    }
}
