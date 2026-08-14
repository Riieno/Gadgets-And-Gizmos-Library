package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.UUID;

// Create a Sable rotary joint which can move through local yaw while locking every other axis
public final class SableYawJointApi {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final double MINIMUM_AXIS_LENGTH_SQUARED = 1.0E-12D;
    private static final String[] ROTARY_CONFIGURATION_CLASSES = {
            "dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintConfiguration",
            "dev.ryanhcode.sable.api.physics.constraint.rotary.RotaryConstraintConfiguration"
    };

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the sable yaw joint API
    private SableYawJointApi() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create a local yaw joint and leave contact between both bodies disabled by default
    public static Joint create(@Nullable ServerSubLevel first,
                               @Nullable ServerSubLevel second,
                               @Nullable Vector3dc firstLocalAnchor,
                               @Nullable Vector3dc secondLocalAnchor,
                               @Nullable Vector3dc firstLocalYawAxis,
                               @Nullable Vector3dc secondLocalYawAxis) {
        return create(first, second, firstLocalAnchor, secondLocalAnchor,
                firstLocalYawAxis, secondLocalYawAxis, false);
    }

    // Create a local yaw joint with an explicit linked-body contact setting
    public static Joint create(@Nullable ServerSubLevel first,
                               @Nullable ServerSubLevel second,
                               @Nullable Vector3dc firstLocalAnchor,
                               @Nullable Vector3dc secondLocalAnchor,
                               @Nullable Vector3dc firstLocalYawAxis,
                               @Nullable Vector3dc secondLocalYawAxis,
                               boolean contactsEnabled) {
        UUID firstId = id(first);
        UUID secondId = id(second);
        if (first == second || firstId != null && firstId.equals(secondId)) {
            return Joint.unavailable(firstId, secondId);
        }
        try {
            if (!usable(first) || !usable(second)
                    || first.getLevel() != second.getLevel()) {
                return Joint.unavailable(firstId, secondId);
            }
            Vector3d firstAnchor = finiteCopy(firstLocalAnchor);
            Vector3d secondAnchor = finiteCopy(secondLocalAnchor);
            Vector3d firstAxis = normalizedCopy(firstLocalYawAxis);
            Vector3d secondAxis = normalizedCopy(secondLocalYawAxis);
            if (firstAnchor == null || secondAnchor == null
                    || firstAxis == null || secondAxis == null
                    || !validPhysicsBody(first) || !validPhysicsBody(second)) {
                return Joint.unavailable(firstId, secondId);
            }
            ServerSubLevelContainer container = SubLevelContainer.getContainer(first.getLevel());
            if (container == null || container.physicsSystem() == null) {
                return Joint.unavailable(firstId, secondId);
            }
            Object pipeline = container.physicsSystem().getPipeline();
            Object config = createConfig(firstAnchor, secondAnchor, firstAxis, secondAxis);
            PhysicsConstraintHandle handle = addConstraint(pipeline, first, second, config);
            if (handle == null || !safeValid(handle)) {
                safeRemove(handle);
                return Joint.unavailable(firstId, secondId);
            }
            Joint joint = new Joint(firstId, secondId, first, second, pipeline, handle,
                    contactsEnabled);
            if (!joint.setContactsEnabled(contactsEnabled)) {
                joint.remove();
                return Joint.unavailable(firstId, secondId);
            }
            joint.wake();
            return joint;
        } catch (RuntimeException | LinkageError err) {
            return Joint.unavailable(firstId, secondId);
        }
    }

    // Check if this is valid
    public static boolean isValid(@Nullable Joint joint) {
        return joint != null && joint.isValid();
    }

    // Remove the sable yaw joint API
    public static boolean remove(@Nullable Joint joint) {
        return joint != null && joint.remove();
    }

    // Wake the sable yaw joint API
    public static boolean wake(@Nullable Joint joint) {
        return joint != null && joint.wake();
    }

    // Check if contact setting is enabled
    public static boolean setContactsEnabled(@Nullable Joint joint, boolean enabled) {
        return joint != null && joint.setContactsEnabled(enabled);
    }

    // Apply a yaw position servo without a force limit
    public static boolean setYawServo(@Nullable Joint joint,
                                      double targetAngleRadians,
                                      double stiffness,
                                      double damping) {
        return joint != null && joint.setYawServo(
                targetAngleRadians, stiffness, damping);
    }

    // Apply a yaw position servo with a maximum force
    public static boolean setYawServo(@Nullable Joint joint,
                                      double targetAngleRadians,
                                      double stiffness,
                                      double damping,
                                      double maximumForce) {
        return joint != null && joint.setYawServo(
                targetAngleRadians, stiffness, damping, maximumForce);
    }

    // Remove spring and dampening from the joint's yaw axis
    public static boolean disableYawServo(@Nullable Joint joint) {
        return joint != null && joint.disableYawServo();
    }

    // Build a smooth response which stays off through the free angle and caps at the maximum angle
    public static ProgressiveYawResponse progressiveResponse(
            double angleRadians,
            double freeAngleRadians,
            double maximumAngleRadians,
            double maximumStiffness,
            double maximumDamping,
            double maximumForce
    ) {
        if (!Double.isFinite(angleRadians)
                || !finiteNonNegative(freeAngleRadians)
                || !Double.isFinite(maximumAngleRadians)
                || maximumAngleRadians <= freeAngleRadians
                || !finiteNonNegative(maximumStiffness)
                || !finiteNonNegative(maximumDamping)
                || !finiteNonNegative(maximumForce)) {
            return ProgressiveYawResponse.DISABLED;
        }
        double magnitude = Math.abs(angleRadians);
        if (magnitude <= freeAngleRadians) {
            return ProgressiveYawResponse.DISABLED;
        }
        double progress = Math.min(1.0D,
                (magnitude - freeAngleRadians)
                        / (maximumAngleRadians - freeAngleRadians));
        double weight = progress * progress * (3.0D - 2.0D * progress);
        return new ProgressiveYawResponse(
                weight,
                maximumStiffness * weight,
                maximumDamping * weight,
                maximumForce * weight);
    }

    // Create the config
    private static @Nullable Object createConfig(
            Vector3dc firstAnchor, Vector3dc secondAnchor,
            Vector3dc firstAxis, Vector3dc secondAxis
    ) {
        for (String className : ROTARY_CONFIGURATION_CLASSES) {
            try {
                Class<?> type = Class.forName(className);
                Constructor<?> constructor = type.getConstructor(Vector3dc.class, Vector3dc.class,
                        Vector3dc.class, Vector3dc.class);
                Object config = constructor.newInstance(
                        new Vector3d(firstAnchor), new Vector3d(secondAnchor),
                        new Vector3d(firstAxis), new Vector3d(secondAxis));
                if (config instanceof PhysicsConstraintConfiguration<?>) {
                    return config;
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            }
        }
        return null;
    }

    // Add the constraint
    private static @Nullable PhysicsConstraintHandle addConstraint(
            @Nullable Object pipeline, ServerSubLevel first,
            ServerSubLevel second, @Nullable Object config
    ) {
        if (pipeline == null || config == null) {
            return null;
        }
        for (Method method : pipeline.getClass().getMethods()) {
            if (!"addConstraint".equals(method.getName()) || method.getParameterCount() != 3) {
                continue;
            }
            Class<?>[] parameters = method.getParameterTypes();
            if (!parameters[0].isInstance(first) || !parameters[1].isInstance(second)
                    || !parameters[2].isInstance(config)) {
                continue;
            }
            try {
                Object handle = method.invoke(pipeline, first, second, config);
                return handle instanceof PhysicsConstraintHandle constraintHandle
                        ? constraintHandle : null;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return null;
            }
        }
        return null;
    }

    // Wake the body
    private static boolean wakeBody(@Nullable Object pipeline, @Nullable ServerSubLevel body) {
        if (pipeline == null || !usable(body)) {
            return false;
        }
        for (Method method : pipeline.getClass().getMethods()) {
            if (!"wakeUp".equals(method.getName()) || method.getParameterCount() != 1
                    || !method.getParameterTypes()[0].isInstance(body)) {
                continue;
            }
            try {
                method.invoke(pipeline, body);
                return true;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return false;
            }
        }
        return false;
    }

    // Check if the physics body is valid
    private static boolean validPhysicsBody(ServerSubLevel body) {
        try {
            RigidBodyHandle handle = RigidBodyHandle.of(body);
            return handle != null && handle.isValid();
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    // Check if the sublevel can be used
    private static boolean usable(@Nullable ServerSubLevel body) {
        try {
            return body != null && !body.isRemoved() && body.getLevel() != null
                    && body.getUniqueId() != null;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    // Get the id
    private static @Nullable UUID id(@Nullable ServerSubLevel body) {
        try {
            return body == null ? null : body.getUniqueId();
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    // Copy the vector with finite values
    private static @Nullable Vector3d finiteCopy(@Nullable Vector3dc val) {
        if (val == null || !Double.isFinite(val.x())
                || !Double.isFinite(val.y()) || !Double.isFinite(val.z())) {
            return null;
        }
        return new Vector3d(val);
    }

    // Get the normalized copy
    private static @Nullable Vector3d normalizedCopy(@Nullable Vector3dc val) {
        Vector3d res = finiteCopy(val);
        if (res == null || res.lengthSquared() <= MINIMUM_AXIS_LENGTH_SQUARED) {
            return null;
        }
        return res.normalize();
    }

    // Check if this is finite non-negative
    private static boolean finiteNonNegative(double val) {
        return Double.isFinite(val) && val >= 0.0D;
    }

    // Check if the safe is valid
    private static boolean safeValid(@Nullable PhysicsConstraintHandle handle) {
        try {
            return handle != null && handle.isValid();
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    // Safely remove the yaw joint
    private static void safeRemove(@Nullable PhysicsConstraintHandle handle) {
        if (handle == null) {
            return;
        }
        try {
            if (handle.isValid()) {
                handle.remove();
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    // Manage the Sable yaw joint
    public static final class Joint implements AutoCloseable {
        // First sub-level id
        private final @Nullable UUID firstSubLevelId;
        // Second sub-level id
        private final @Nullable UUID secondSubLevelId;
        // First entry
        private final @Nullable ServerSubLevel first;
        // Second entry
        private final @Nullable ServerSubLevel second;
        // Pipeline
        private final @Nullable Object pipeline;
        // Current handle
        private @Nullable PhysicsConstraintHandle handle;
        // Tracks whether contacts are enabled
        private boolean contactsEnabled;

        // Initialize the joint
        private Joint(@Nullable UUID firstSubLevelId, @Nullable UUID secondSubLevelId,
                      @Nullable ServerSubLevel first, @Nullable ServerSubLevel second,
                      @Nullable Object pipeline, @Nullable PhysicsConstraintHandle handle,
                      boolean contactsEnabled) {
            this.firstSubLevelId = firstSubLevelId;
            this.secondSubLevelId = secondSubLevelId;
            this.first = first;
            this.second = second;
            this.pipeline = pipeline;
            this.handle = handle;
            this.contactsEnabled = contactsEnabled;
        }

        // Get the first sublevel id
        public @Nullable UUID firstSubLevelId() {
            return firstSubLevelId;
        }

        // Get the second sublevel id
        public @Nullable UUID secondSubLevelId() {
            return secondSubLevelId;
        }

        // Check if this is valid
        public synchronized boolean isValid() {
            return usable(first) && usable(second) && safeValid(handle);
        }

        // Check if contacts are enabled
        public synchronized boolean contactsEnabled() {
            return contactsEnabled;
        }

        // Check if contact setting is enabled
        public synchronized boolean setContactsEnabled(boolean enabled) {
            if (!isValid()) {
                return false;
            }
            try {
                handle.setContactsEnabled(enabled);
                contactsEnabled = enabled;
                return true;
            } catch (RuntimeException | LinkageError ignored) {
                return false;
            }
        }

        // Apply a yaw position servo without a force limit
        public synchronized boolean setYawServo(double targetAngleRadians,
                                                double stiffness,
                                                double damping) {
            return applyYawServo(targetAngleRadians, stiffness, damping,
                    false, 0.0D);
        }

        // Apply a yaw position servo with a maximum force
        public synchronized boolean setYawServo(double targetAngleRadians,
                                                double stiffness,
                                                double damping,
                                                double maximumForce) {
            return applyYawServo(targetAngleRadians, stiffness, damping,
                    true, maximumForce);
        }

        // Turn the yaw servo off without removing the joint
        public synchronized boolean disableYawServo() {
            return applyYawServo(0.0D, 0.0D, 0.0D,
                    true, 0.0D);
        }

        // Apply the yaw servo
        private boolean applyYawServo(double targetAngleRadians,
                                      double stiffness,
                                      double damping,
                                      boolean forceLimited,
                                      double maximumForce) {
            if (!Double.isFinite(targetAngleRadians)
                    || !finiteNonNegative(stiffness)
                    || !finiteNonNegative(damping)
                    || forceLimited && !finiteNonNegative(maximumForce)
                    || !isValid()) {
                return false;
            }
            PhysicsConstraintHandle current = handle;
            if (current == null) {
                return false;
            }
            try {
                current.setMotor(ConstraintJointAxis.ANGULAR_X,
                        targetAngleRadians, stiffness, damping,
                        forceLimited, maximumForce);
                return true;
            } catch (RuntimeException | LinkageError ignored) {
                return false;
            }
        }

        // Wake the joint
        public synchronized boolean wake() {
            boolean firstWoken = wakeBody(pipeline, first);
            boolean secondWoken = wakeBody(pipeline, second);
            return firstWoken && secondWoken;
        }

        // Remove the joint
        public synchronized boolean remove() {
            PhysicsConstraintHandle current = handle;
            if (current == null) {
                return false;
            }
            try {
                if (!current.isValid()) {
                    handle = null;
                    return false;
                }
                current.remove();
                handle = null;
            } catch (RuntimeException | LinkageError ignored) {
                return false;
            }
            wakeBody(pipeline, first);
            wakeBody(pipeline, second);
            return true;
        }

        // Close the joint
        @Override
        public void close() {
            remove();
        }

        // Create an unavailable joint
        private static Joint unavailable(@Nullable UUID firstSubLevelId,
                                         @Nullable UUID secondSubLevelId) {
            return new Joint(firstSubLevelId, secondSubLevelId,
                    null, null, null, null, false);
        }
    }

    // Store the spring, dampening and force values returned by the progressive response curve
    public record ProgressiveYawResponse(double weight,
                                         double stiffness,
                                         double damping,
                                         double maximumForce) {
        private static final ProgressiveYawResponse DISABLED =
                new ProgressiveYawResponse(0.0D, 0.0D, 0.0D, 0.0D);

        // Initialize the progressive yaw response
        public ProgressiveYawResponse {
            weight = Double.isFinite(weight)
                    ? Math.max(0.0D, Math.min(1.0D, weight)) : 0.0D;
            stiffness = finiteNonNegative(stiffness) ? stiffness : 0.0D;
            damping = finiteNonNegative(damping) ? damping : 0.0D;
            maximumForce = finiteNonNegative(maximumForce) ? maximumForce : 0.0D;
        }

        // Check if this is active
        public boolean active() {
            return weight > 0.0D && maximumForce > 0.0D
                    && (stiffness > 0.0D || damping > 0.0D);
        }
    }
}
