package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

// Drive a vehicle's native pilot while its own controller owns the actuators and gait
public interface ScmPilotControl extends ScmControlProbe{
    // Apply simultaneous movement and rotation in the target body's local frame
    void drive(Vec3 force, Vec3 torque);

    // Preserve native jump and crouch buttons independently of translational lift
    default void drive(Vec3 force, Vec3 torque, boolean jump, boolean crouch){ drive(force, torque); }

    // Carry absolute navigation speed and connector error alongside native motion inputs
    default void drive(Command command){ drive(command.force(), command.torque(), command.jump(), command.crouch()); }

    // Report the native locomotion supported by the currently loaded running gear
    default Profile profile(){ return Profile.GROUND; }

    // Describe live capability without exposing an optional mod's implementation classes
    record Profile(boolean canClimb, boolean canFly, boolean flying){
        public static final Profile GROUND = new Profile(false, false, false);
    }

    // Express navigation offset and velocity in the same local frame as pilot demand
    record Navigation(Vec3 offset, Vec3 velocity, double speed, double tolerance, boolean docking, boolean altitudeOnly){
        // Preserve existing full navigation callers
        public Navigation(Vec3 offset, Vec3 velocity, double speed, double tolerance, boolean docking){
            this(offset, velocity, speed, tolerance, docking, false);
        }

        // Keep unavailable speed and position data neutral
        public Navigation{
            offset = finite(offset);
            velocity = finite(velocity);
            speed = Double.isFinite(speed) ? Math.max(0, speed) : 0;
            tolerance = Double.isFinite(tolerance) ? Math.max(0, tolerance) : 0;
        }
    }

    // Keep transient pilot requests independent of graph commands and schedule implementations
    record Command(Vec3 force, Vec3 torque, boolean jump, boolean crouch, boolean hover, @Nullable Navigation navigation, double maximumSpeed){
        // Preserve existing native pilot callers without imposing a speed limit
        public Command(Vec3 force, Vec3 torque, boolean jump, boolean crouch, boolean hover, @Nullable Navigation navigation){
            this(force, torque, jump, crouch, hover, navigation, 0);
        }

        // Sanitize motion before passing it to native vehicle code
        public Command{
            force = finite(force);
            torque = finite(torque);
            maximumSpeed = new ScmSpeedLimit(maximumSpeed).blocksPerSecond();
        }
    }

    // Replace non-finite vector components with neutral input
    private static Vec3 finite(Vec3 val){
        if(val == null) return Vec3.ZERO;
        return new Vec3(Double.isFinite(val.x) ? val.x : 0, Double.isFinite(val.y) ? val.y : 0,
                Double.isFinite(val.z) ? val.z : 0);
    }

    // Resolve bounded pilot axes from local SCM demand and the vehicle's native orientation
    static Input input(Vec3 force, Vec3 torque, Vec3 forward, Vec3 up){
        Vec3 fwd = forward.normalize();
        Vec3 vertical = up.normalize();
        Vec3 right = fwd.cross(vertical).normalize();
        return new Input(force.dot(fwd), force.dot(right), force.dot(vertical),
                ScmControlAxes.yawRightDemand(torque, vertical, false), torque.dot(right), torque.dot(fwd));
    }

    // Keep native pilot inputs finite and within their signed unit range
    record Input(double forward, double strafe, double lift, double yaw, double pitch, double roll){
        // Sanitize every axis before passing it to an optional vehicle controller
        public Input{
            forward = clamp(forward);
            strafe = clamp(strafe);
            lift = clamp(lift);
            yaw = clamp(yaw);
            pitch = clamp(pitch);
            roll = clamp(roll);
        }

        // Return neutral for invalid demand
        private static double clamp(double val){ return Double.isFinite(val) ? Math.clamp(val, -1.0D, 1.0D) : 0.0D; }
    }
}
