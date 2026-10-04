package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** A transient, controller-relative view of an autopilot's requested control. */
public record ScmControlTelemetry(boolean active, Axes correction, Axes demand,
                                  double driveDirection, double acceleration,
                                  double deceleration, double brake) {
    public static final ScmControlTelemetry IDLE = new ScmControlTelemetry(
            false, Axes.ZERO, Axes.ZERO, 0.0D, 0.0D, 0.0D, 0.0D);

    public ScmControlTelemetry {
        correction = correction == null ? Axes.ZERO : correction;
        demand = demand == null ? Axes.ZERO : demand;
        driveDirection = bounded(driveDirection);
        acceleration = unit(acceleration);
        deceleration = unit(deceleration);
        brake = unit(brake);
    }

    public static ScmControlTelemetry fromVectors(Vec3 correctionForce, Vec3 correctionTorque,
                                                  Vec3 demandForce, Vec3 demandTorque,
                                                  Vec3 forward, Vec3 up, double driveDirection,
                                                  double acceleration, double deceleration, double brake) {
        return new ScmControlTelemetry(true,
                Axes.fromVectors(correctionForce, correctionTorque, forward, up),
                Axes.fromVectors(demandForce, demandTorque, forward, up),
                driveDirection, acceleration, deceleration, brake);
    }

    private static double bounded(double value) {
        return Double.isFinite(value) && value != 0.0D
                ? Mth.clamp(value, -1.0D, 1.0D) : 0.0D;
    }

    private static double unit(double value) {
        return Double.isFinite(value) ? Mth.clamp(value, 0.0D, 1.0D) : 0.0D;
    }

    public record Axes(double pitch, double yaw, double roll,
                       double throttle, double strafe, double lift) {
        public static final Axes ZERO = new Axes(0.0D, 0.0D, 0.0D,
                0.0D, 0.0D, 0.0D);

        public Axes {
            pitch = bounded(pitch);
            yaw = bounded(yaw);
            roll = bounded(roll);
            throttle = bounded(throttle);
            strafe = bounded(strafe);
            lift = bounded(lift);
        }

        public static Axes fromVectors(Vec3 force, Vec3 torque, Vec3 forward, Vec3 up) {
            if (!finite(forward) || !finite(up) || forward.lengthSqr() < 1.0E-12D
                    || up.lengthSqr() < 1.0E-12D) return ZERO;
            Vec3 front = forward.normalize();
            Vec3 right = front.cross(up).normalize();
            if (!finite(right) || right.lengthSqr() < 1.0E-12D) return ZERO;
            Vec3 top = right.cross(front).normalize();
            Vec3 requestedForce = finite(force) ? force : Vec3.ZERO;
            Vec3 requestedTorque = finite(torque) ? torque : Vec3.ZERO;
            return new Axes(requestedTorque.dot(right), -requestedTorque.dot(top),
                    requestedTorque.dot(front), requestedForce.dot(front),
                    requestedForce.dot(right), requestedForce.dot(top));
        }

        public double value(String axis) {
            return switch (axis) {
                case "pitch" -> pitch;
                case "yaw" -> yaw;
                case "roll" -> roll;
                case "throttle" -> throttle;
                case "strafe" -> strafe;
                case "lift" -> lift;
                default -> 0.0D;
            };
        }

        private static boolean finite(Vec3 vector) {
            return vector != null && Double.isFinite(vector.x)
                    && Double.isFinite(vector.y) && Double.isFinite(vector.z);
        }
    }
}
