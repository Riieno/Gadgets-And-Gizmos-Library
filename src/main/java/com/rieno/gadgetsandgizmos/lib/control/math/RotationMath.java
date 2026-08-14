package com.rieno.gadgetsandgizmos.lib.control.math;

// Convert rotations between quaternions, ZXZ Euler angles and XYZ Tait-Bryan angles
public final class RotationMath {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final double SINGULAR_EPSILON = 1.0E-10D;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the rotation math
    private RotationMath() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the euler zxz to quaternion
    public static Quaternion eulerZxzToQuaternion(Vector3 radians) {
        double alpha = radians.x() * 0.5D;
        double beta = radians.y() * 0.5D;
        double gamma = radians.z() * 0.5D;
        double sinBeta = Math.sin(beta);
        double cosBeta = Math.cos(beta);
        double difference = alpha - gamma;
        double sum = alpha + gamma;
        return new Quaternion(
                sinBeta * Math.cos(difference),
                sinBeta * Math.sin(difference),
                cosBeta * Math.sin(sum),
                cosBeta * Math.cos(sum)).normalized();
    }

    // Get the quaternion to euler zxz
    public static Vector3 quaternionToEulerZxz(Quaternion value) {
        Quaternion q = value.normalized();
        double r02 = 2.0D * (q.x() * q.z() + q.y() * q.w());
        double r12 = 2.0D * (q.y() * q.z() - q.x() * q.w());
        double r20 = 2.0D * (q.x() * q.z() - q.y() * q.w());
        double r21 = 2.0D * (q.y() * q.z() + q.x() * q.w());
        double r22 = 1.0D - 2.0D * (q.x() * q.x() + q.y() * q.y());
        double beta = Math.acos(clampUnit(r22));
        double sinBeta = Math.sin(beta);
        if (Math.abs(sinBeta) < SINGULAR_EPSILON) {
            double r00 = 1.0D - 2.0D * (q.y() * q.y() + q.z() * q.z());
            double r10 = 2.0D * (q.x() * q.y() + q.z() * q.w());
            return new Vector3(Math.atan2(r10, r00), beta, 0.0D);
        }
        return new Vector3(Math.atan2(r02, -r12), beta, Math.atan2(r20, r21));
    }

    // Get the tait bryan xyz to quaternion
    public static Quaternion taitBryanXyzToQuaternion(Vector3 radians) {
        double roll = radians.x() * 0.5D;
        double pitch = radians.y() * 0.5D;
        double yaw = radians.z() * 0.5D;
        double cr = Math.cos(roll);
        double sr = Math.sin(roll);
        double cp = Math.cos(pitch);
        double sp = Math.sin(pitch);
        double cy = Math.cos(yaw);
        double sy = Math.sin(yaw);
        return new Quaternion(
                sr * cp * cy - cr * sp * sy,
                cr * sp * cy + sr * cp * sy,
                cr * cp * sy - sr * sp * cy,
                cr * cp * cy + sr * sp * sy).normalized();
    }

    // Get the quaternion to tait bryan xyz
    public static Vector3 quaternionToTaitBryanXyz(Quaternion value) {
        Quaternion q = value.normalized();
        double roll = Math.atan2(
                2.0D * (q.w() * q.x() + q.y() * q.z()),
                1.0D - 2.0D * (q.x() * q.x() + q.y() * q.y()));
        double pitch = Math.asin(clampUnit(2.0D * (q.w() * q.y() - q.z() * q.x())));
        double yaw = Math.atan2(
                2.0D * (q.w() * q.z() + q.x() * q.y()),
                1.0D - 2.0D * (q.y() * q.y() + q.z() * q.z()));
        return new Vector3(roll, pitch, yaw);
    }

    // Get the euler zxz to tait bryan xyz
    public static Vector3 eulerZxzToTaitBryanXyz(Vector3 radians) {
        return quaternionToTaitBryanXyz(eulerZxzToQuaternion(radians));
    }

    // Get the tait bryan xyz to euler zxz
    public static Vector3 taitBryanXyzToEulerZxz(Vector3 radians) {
        return quaternionToEulerZxz(taitBryanXyzToQuaternion(radians));
    }

    // Clamp the unit
    private static double clampUnit(double val) {
        return Math.max(-1.0D, Math.min(1.0D, val));
    }
}
