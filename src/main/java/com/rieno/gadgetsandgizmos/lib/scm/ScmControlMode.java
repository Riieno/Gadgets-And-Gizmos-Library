package com.rieno.gadgetsandgizmos.lib.scm;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

// Convert one navigation sample into vehicle-specific SCM control demand
public interface ScmControlMode {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the id
    ResourceLocation id();

    // Get the SCM control mode display name
    String displayName();

    // Navigate the SCM control mode
    ControlOutput navigate(ControlInput input);

    // Store the control input
    record ControlInput(
            Vec3 position,
            Vec3 velocity,
            Vec3 angularVelocity,
            Vec3 forward,
            Vec3 up,
            Vec3 right,
            Vec3 target,
            Vec3 pathDirection,
            Vec3 accumulatedError,
            double targetSpeed,
            double tolerance,
            double distanceResponse,
            boolean transitWaypoint,
            double forwardClearance,
            double reverseClearance,
            double travelSpeedLimit,
            double propulsion,
            boolean avoidCollisions,
            boolean preferForward,
            boolean reverseRecovery
    ) {
        // Preserve the original public constructor for existing control-mode
        // integrations. Reverse recovery is opt-in and is supplied only by a
        // route planner that has exhausted its forward-first alternatives.
        public ControlInput(
                Vec3 position,
                Vec3 velocity,
                Vec3 angularVelocity,
                Vec3 forward,
                Vec3 up,
                Vec3 right,
                Vec3 target,
                Vec3 pathDirection,
                Vec3 accumulatedError,
                double targetSpeed,
                double tolerance,
                double distanceResponse,
                double forwardClearance,
                double reverseClearance,
                boolean avoidCollisions,
                boolean preferForward
        ) {
            this(position, velocity, angularVelocity, forward, up, right, target,
                    pathDirection, accumulatedError, targetSpeed, tolerance,
                    distanceResponse, false, forwardClearance, reverseClearance,
                    Double.MAX_VALUE, -1.0D,
                    avoidCollisions, preferForward, false);
        }

        // Preserve the current public constructor while allowing the runtime
        // to pass a physical stopping limit and a requested propulsion level.
        public ControlInput(
                Vec3 position,
                Vec3 velocity,
                Vec3 angularVelocity,
                Vec3 forward,
                Vec3 up,
                Vec3 right,
                Vec3 target,
                Vec3 pathDirection,
                Vec3 accumulatedError,
                double targetSpeed,
                double tolerance,
                double distanceResponse,
                double forwardClearance,
                double reverseClearance,
                boolean avoidCollisions,
                boolean preferForward,
                boolean reverseRecovery
        ) {
            this(position, velocity, angularVelocity, forward, up, right, target,
                    pathDirection, accumulatedError, targetSpeed, tolerance,
                    distanceResponse, false, forwardClearance, reverseClearance,
                    Double.MAX_VALUE, -1.0D,
                    avoidCollisions, preferForward, reverseRecovery);
        }

        // Initialize the control input
        public ControlInput {
            position = finite(position);
            velocity = finite(velocity);
            angularVelocity = finite(angularVelocity);
            forward = normalize(forward, new Vec3(0.0D, 0.0D, 1.0D));
            up = normalize(up, new Vec3(0.0D, 1.0D, 0.0D));
            right = normalize(right, forward.cross(up));
            target = finite(target);
            pathDirection = normalize(pathDirection, normalize(target.subtract(position), forward));
            accumulatedError = finite(accumulatedError);
            targetSpeed = Math.max(0.0D, finite(targetSpeed));
            tolerance = Math.max(0.0D, finite(tolerance));
            distanceResponse = Math.max(0.0D, finite(distanceResponse));
            forwardClearance = Math.max(0.0D, finite(forwardClearance));
            reverseClearance = Math.max(0.0D, finite(reverseClearance));
            travelSpeedLimit = Double.isFinite(travelSpeedLimit)
                    ? Math.max(0.0D, travelSpeedLimit) : Double.MAX_VALUE;
            // A negative value means that the caller did not supply a direct
            // propulsion request and the mode must use its target-speed
            // controller. Clamping -1 to zero changed that sentinel into an
            // explicit zero-throttle command, which left scheduled craft only
            // moving through small position/gravity correction terms.
            propulsion = Double.isFinite(propulsion) && propulsion >= 0.0D
                    ? Mth.clamp(propulsion, 0.0D, 1.0D) : -1.0D;
        }

        // Check whether this command asks for a direct propulsion level.
        public boolean hasPropulsionRequest() {
            return propulsion >= 0.0D;
        }
    }

    // Store the control output
    record ControlOutput(
            Vec3 force,
            Vec3 torque,
            boolean compensateGravity,
            double uprightStabilization,
            double driveDirection,
            double driveStrength
    ) {
        // Preserve the original output constructor for third-party control
        // modes. Legacy outputs derive their analogue drive level from their
        // authored force, while built-in modes now provide it explicitly.
        public ControlOutput(
                Vec3 force,
                Vec3 torque,
                boolean compensateGravity,
                double uprightStabilization,
                double driveDirection
        ) {
            this(force, torque, compensateGravity, uprightStabilization, driveDirection,
                    force == null ? 0.0D : force.length());
        }

        // Initialize the control output
        public ControlOutput {
            force = finite(force);
            torque = finite(torque);
            uprightStabilization = Mth.clamp(finite(uprightStabilization), 0.0D, 1.0D);
            driveDirection = Mth.clamp(finite(driveDirection), -1.0D, 1.0D);
            driveStrength = Mth.clamp(finite(driveStrength), 0.0D, 1.0D);
        }
    }

    // Normalize the SCM control mode
    private static Vec3 normalize(Vec3 val, Vec3 fallback) {
        Vec3 finiteValue = finite(val);
        if (finiteValue.lengthSqr() > 1.0E-12D) {
            return finiteValue.normalize();
        }
        Vec3 finiteFallback = finite(fallback);
        return finiteFallback.lengthSqr() > 1.0E-12D
                ? finiteFallback.normalize() : Vec3.ZERO;
    }

    // Normalize the value to a finite result
    private static Vec3 finite(Vec3 val) {
        return val == null ? Vec3.ZERO : new Vec3(
                finite(val.x), finite(val.y), finite(val.z));
    }

    // Normalize the value to a finite result
    private static double finite(double val) {
        return Double.isFinite(val) ? val : 0.0D;
    }
}
