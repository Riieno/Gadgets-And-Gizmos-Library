package com.rieno.gadgetsandgizmos.lib.scm;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

// Define the built-in airship, plane and car SCM control modes
public final class ScmBuiltinControlModes {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final String NAMESPACE = "createthrusters";
    public static final ResourceLocation AIRSHIP_ID = id("airship");
    public static final ResourceLocation PLANE_ID = id("plane");
    public static final ResourceLocation CAR_ID = id("car");
    private static final Set<ResourceLocation> BUILTINS = Set.of(AIRSHIP_ID, PLANE_ID, CAR_ID);
    private static final double MIN_CLEARANCE = 3.0D;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the SCM builtin control modes
    private ScmBuiltinControlModes() {
    }

    // Register the defaults
    static void registerDefaults() {
        ScmControlModeRegistry.register(new AirshipMode());
        ScmControlModeRegistry.register(new PlaneMode());
        ScmControlModeRegistry.register(new CarMode());
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this is builtin
    public static boolean isBuiltin(ResourceLocation id) {
        return BUILTINS.contains(id);
    }

    // Get the id
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }

    // Handle the airship mode
    private static final class AirshipMode implements ScmControlMode {
        // Get the id
        @Override
        public ResourceLocation id() {
            return AIRSHIP_ID;
        }

        // Get the airship mode display name
        @Override
        public String displayName() {
            return "Airship";
        }

        // Navigate the airship mode
        @Override
        public ControlOutput navigate(ControlInput input) {
            Vec3 error = input.target().subtract(input.position());
            double desiredSpeed = targetSpeed(input, error.length());
            Vec3 desiredVelocity = input.pathDirection().scale(desiredSpeed);
            Vec3 force = desiredVelocity.subtract(input.velocity()).scale(0.6D)
                    .add(input.accumulatedError().scale(0.04D));
            if (input.preferForward()) {
                Vec3 forward = horizontal(input.forward());
                Vec3 path = horizontal(input.pathDirection());
                double alignment = Mth.clamp(forward.dot(path), -1.0D, 1.0D);
                double engagement = Mth.clamp((alignment + 0.15D) / 0.75D, 0.0D, 1.0D);
                double forwardDemand = force.dot(forward);
                if (engagement <= 1.0E-6D) {
                    forwardDemand = Math.min(0.0D,
                            -input.velocity().dot(forward) * 0.8D);
                } else {
                    forwardDemand *= engagement;
                }
                force = new Vec3(0.0D, force.y, 0.0D).add(forward.scale(
                        Mth.clamp(forwardDemand, -1.0D, 1.0D)));
            }
            Vec3 worldUp = new Vec3(0.0D, 1.0D, 0.0D);
            double yawError = signedAngle(
                    horizontal(input.forward()), horizontal(input.pathDirection()), worldUp);
            Vec3 torque = worldUp.scale(Mth.clamp(
                    yawError * 0.5D - input.angularVelocity().dot(worldUp) * 0.25D,
                    -1.0D, 1.0D));
            return new ControlOutput(force, torque, true, 0.75D, 0.0D);
        }
    }

    // Handle the car mode
    private static final class CarMode implements ScmControlMode {
        // Get the id
        @Override
        public ResourceLocation id() {
            return CAR_ID;
        }

        // Get the car mode display name
        @Override
        public String displayName() {
            return "Car";
        }

        // Navigate the car mode
        @Override
        public ControlOutput navigate(ControlInput input) {
            Vec3 worldUp = new Vec3(0.0D, 1.0D, 0.0D);
            Vec3 forward = horizontal(input.forward());
            Vec3 path = horizontal(input.pathDirection());
            if (forward.lengthSqr() <= 1.0E-12D || path.lengthSqr() <= 1.0E-12D) {
                return new ControlOutput(Vec3.ZERO, Vec3.ZERO, false, 0.0D, 0.0D);
            }
            double alignment = Mth.clamp(forward.dot(path), -1.0D, 1.0D);
            double dir = alignment < -0.35D ? -1.0D : 1.0D;
            double clearance = dir > 0.0D
                    ? input.forwardClearance() : input.reverseClearance();
            if (dir > 0.0D && clearance <= MIN_CLEARANCE
                    && input.reverseClearance() > clearance + 1.0D) {
                dir = -1.0D;
                clearance = input.reverseClearance();
            }

            double distance = horizontal(input.target().subtract(input.position())).length();
            double desiredSpeed = targetSpeed(input, distance);
            if (input.avoidCollisions()) {
                desiredSpeed = Math.min(desiredSpeed, safeGroundSpeed(clearance));
            }
            double headingAlignment = Math.max(0.0D, dir * alignment);
            desiredSpeed *= 0.12D + 0.88D * headingAlignment;
            double currentSpeed = input.velocity().dot(forward);
            double throttle = Mth.clamp(
                    (dir * desiredSpeed - currentSpeed) * 0.18D, -1.0D, 1.0D);

            Vec3 facingPath = dir < 0.0D ? path.scale(-1.0D) : path;
            double yawError = signedAngle(forward, facingPath, worldUp);
            double yawRate = input.angularVelocity().dot(worldUp);
            double steering = Mth.clamp(yawError * 1.5D - yawRate * 0.65D, -1.0D, 1.0D);
            if (Math.abs(throttle) < 0.04D && Math.abs(yawError) > 0.2D) {
                throttle = dir * 0.12D;
            }
            return new ControlOutput(
                    forward.scale(throttle), worldUp.scale(steering),
                    false, 0.0D, dir);
        }
    }

    // Handle the plane mode
    private static final class PlaneMode implements ScmControlMode {
        private static final double GRAVITY = 9.81D;
        private static final double MAX_BANK_RADIANS = Math.toRadians(38.0D);

        // Get the id
        @Override
        public ResourceLocation id() {
            return PLANE_ID;
        }

        // Get the plane mode display name
        @Override
        public String displayName() {
            return "Plane";
        }

        // Navigate the plane mode
        @Override
        public ControlOutput navigate(ControlInput input) {
            Vec3 worldUp = new Vec3(0.0D, 1.0D, 0.0D);
            double speed = Math.max(0.1D, input.velocity().length());
            double turnRadius = speed * speed / (GRAVITY * Math.tan(MAX_BANK_RADIANS));
            double predictionSeconds = Mth.clamp(1.0D + turnRadius / Math.max(8.0D, speed * 8.0D),
                    1.0D, 6.0D);
            Vec3 predictedPosition = input.position().add(input.velocity().scale(predictionSeconds));
            Vec3 predictedError = input.target().subtract(predictedPosition);
            Vec3 horizontalPath = horizontal(input.pathDirection());
            if (horizontalPath.lengthSqr() <= 1.0E-12D) {
                horizontalPath = horizontal(predictedError);
            }
            double horizontalDistance = Math.max(1.0D, horizontal(predictedError).length());
            double arcLength = Math.max(horizontalDistance, turnRadius * 0.75D);
            double climbSlope = Mth.clamp(predictedError.y / arcLength, -0.45D, 0.45D);
            Vec3 desiredForward = normalize(
                    horizontalPath.add(worldUp.scale(climbSlope)), input.forward());

            Vec3 horizontalForward = horizontal(input.forward());
            double yawError = signedAngle(horizontalForward, horizontal(desiredForward), worldUp);
            double pitchError = Math.asin(Mth.clamp(desiredForward.y, -1.0D, 1.0D))
                    - Math.asin(Mth.clamp(input.forward().y, -1.0D, 1.0D));
            double desiredBank = Mth.clamp(-yawError * 1.3D,
                    -MAX_BANK_RADIANS, MAX_BANK_RADIANS);
            double currentBank = Math.atan2(input.right().y, Math.max(1.0E-6D, input.up().y));

            double yaw = Mth.clamp(yawError * 0.55D
                    - input.angularVelocity().dot(worldUp) * 0.35D, -0.65D, 0.65D);
            double pitch = Mth.clamp(pitchError * 1.8D
                    - input.angularVelocity().dot(input.right()) * 0.55D, -1.0D, 1.0D);
            double roll = Mth.clamp((desiredBank - currentBank) * 1.6D
                    - input.angularVelocity().dot(input.forward()) * 0.55D, -1.0D, 1.0D);
            Vec3 torque = worldUp.scale(yaw)
                    .add(input.right().scale(pitch))
                    .add(input.forward().scale(roll));

            double desiredSpeed = Math.max(0.35D, targetSpeed(input,
                    input.target().distanceTo(input.position())));
            double throttle = Mth.clamp(0.28D + (desiredSpeed - speed) * 0.12D
                    + Math.max(0.0D, climbSlope) * 0.25D, 0.18D, 1.0D);
            if (input.avoidCollisions() && input.forwardClearance() < MIN_CLEARANCE * 2.0D) {
                throttle = Math.max(0.18D, throttle * 0.55D);
            }
            return new ControlOutput(input.forward().scale(throttle), torque,
                    false, 0.0D, 1.0D);
        }
    }

    // Get the target speed
    private static double targetSpeed(ScmControlMode.ControlInput input, double distance) {
        if (distance <= Math.max(0.01D, input.tolerance() * 0.1D)) {
            return 0.0D;
        }
        return Math.min(input.targetSpeed(),
                distance * Math.max(0.0D, input.distanceResponse()));
    }

    // Get the safe ground speed
    private static double safeGroundSpeed(double clearance) {
        return Math.sqrt(5.0D * Math.max(0.0D, clearance - MIN_CLEARANCE));
    }

    // Get the signed angle
    private static double signedAngle(Vec3 from, Vec3 to, Vec3 normal) {
        Vec3 first = normalize(from, Vec3.ZERO);
        Vec3 second = normalize(to, first);
        return Math.atan2(normal.dot(first.cross(second)),
                Mth.clamp(first.dot(second), -1.0D, 1.0D));
    }

    // Get the horizontal
    private static Vec3 horizontal(Vec3 val) {
        return normalize(new Vec3(val.x, 0.0D, val.z), Vec3.ZERO);
    }

    // Normalize the SCM builtin control modes
    private static Vec3 normalize(Vec3 val, Vec3 fallback) {
        if (val != null && val.lengthSqr() > 1.0E-12D) {
            return val.normalize();
        }
        return fallback != null && fallback.lengthSqr() > 1.0E-12D
                ? fallback.normalize() : Vec3.ZERO;
    }
}
