package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

// Check and apply finite point impulses to loaded Sable bodies
public final class SablePointImpulseApi {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final double MIN_DIRECTION_LENGTH_SQUARED = 1.0E-12D;
    private static final double MIN_IMPULSE_MAGNITUDE = 1.0E-12D;
    private static final double MIN_IMPULSE_LENGTH_SQUARED = 1.0E-24D;
    private static final Vector3dc ZERO_IMPULSE = new Vector3d();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the sable point impulse API
    private SablePointImpulseApi() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Apply the directional
    public static boolean applyDirectional(
            @Nullable ServerSubLevel subLevel,
            @Nullable RigidBodyHandle handle,
            @Nullable ForceGroup forceGroup,
            @Nullable Vector3dc point,
            @Nullable Vector3dc direction,
            double magnitude,
            double timeStep) {
        if (!isFinite(direction) || !Double.isFinite(magnitude)
                || !Double.isFinite(timeStep) || timeStep <= 0.0D) {
            return false;
        }

        double directionLengthSquared = direction.lengthSquared();
        if (!Double.isFinite(directionLengthSquared)
                || directionLengthSquared < MIN_DIRECTION_LENGTH_SQUARED) {
            return false;
        }

        double impulseMagnitude = magnitude * timeStep;
        if (!Double.isFinite(impulseMagnitude)
                || Math.abs(impulseMagnitude) < MIN_IMPULSE_MAGNITUDE) {
            return false;
        }

        double scale = impulseMagnitude / Math.sqrt(directionLengthSquared);
        Vector3d impulse = direction.mul(scale, new Vector3d());
        return apply(subLevel, handle, forceGroup, point, impulse);
    }

    // Apply the sable point impulse API
    public static boolean apply(
            @Nullable ServerSubLevel subLevel,
            @Nullable RigidBodyHandle handle,
            @Nullable ForceGroup forceGroup,
            @Nullable Vector3dc point,
            @Nullable Vector3dc impulse) {
        if (subLevel == null || subLevel.isRemoved()
                || handle == null || !handle.isValid()
                || forceGroup == null || !isFinite(point) || !isFinite(impulse)) {
            return false;
        }

        double impulseLengthSquared = impulse.lengthSquared();
        if (!Double.isFinite(impulseLengthSquared)
                || impulseLengthSquared < MIN_IMPULSE_LENGTH_SQUARED) {
            return false;
        }

        MassData massData = subLevel.getMassTracker();
        if (!isValid(massData)) {
            return false;
        }

        Vector3d torque = point.sub(massData.getCenterOfMass(), new Vector3d())
                .cross(impulse, new Vector3d());
        if (!isFinite(torque)) {
            return false;
        }

        QueuedForceGroup queuedForces = subLevel.getOrCreateQueuedForceGroup(forceGroup);
        Vector3dc currentForce = queuedForces.getForceTotal().getLocalForce();
        Vector3dc currentTorque = queuedForces.getForceTotal().getLocalTorque();
        if (!isFinite(currentForce) || !isFinite(currentTorque)) {
            queuedForces.reset();
            currentForce = queuedForces.getForceTotal().getLocalForce();
            currentTorque = queuedForces.getForceTotal().getLocalTorque();
        }

        Vector3d accumulatedForce = currentForce.add(impulse, new Vector3d());
        Vector3d accumulatedTorque = currentTorque.add(torque, new Vector3d());
        if (!isFinite(accumulatedForce) || !isFinite(accumulatedTorque)) {
            return false;
        }

        queuedForces.applyAndRecordPointForce(new Vector3d(point), new Vector3d(impulse));
        handle.applyLinearAndAngularImpulse(ZERO_IMPULSE, ZERO_IMPULSE, true);
        return true;
    }

    // Check if this is valid
    private static boolean isValid(@Nullable MassData massData) {
        return massData != null
                && Double.isFinite(massData.getMass())
                && massData.getMass() > 0.0D
                && Double.isFinite(massData.getInverseMass())
                && isFinite(massData.getInertiaTensor())
                && isFinite(massData.getInverseInertiaTensor())
                && isFinite(massData.getCenterOfMass());
    }

    // Check if this is finite
    private static boolean isFinite(@Nullable Vector3dc vector) {
        return vector != null
                && Double.isFinite(vector.x())
                && Double.isFinite(vector.y())
                && Double.isFinite(vector.z());
    }

    // Check if this is finite
    private static boolean isFinite(@Nullable Matrix3dc matrix) {
        return matrix != null
                && Double.isFinite(matrix.m00()) && Double.isFinite(matrix.m01()) && Double.isFinite(matrix.m02())
                && Double.isFinite(matrix.m10()) && Double.isFinite(matrix.m11()) && Double.isFinite(matrix.m12())
                && Double.isFinite(matrix.m20()) && Double.isFinite(matrix.m21()) && Double.isFinite(matrix.m22());
    }
}
