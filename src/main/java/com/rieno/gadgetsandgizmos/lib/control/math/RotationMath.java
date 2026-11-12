package com.rieno.gadgetsandgizmos.lib.control.math;

// Convert rotations between quaternions, proper Euler angles and Tait-Bryan angles
public final class RotationMath {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final double SINGULAR_EPSILON = 1.0E-10D;
    private static final String[] HORIZONTAL_CARDINALS = {
            "South", "South East", "East", "North East", "North", "North West", "West", "South West"
    };

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

    // Read a horizontal heading from +Z toward +X, with no heading needed directly above or below
    public static double horizontalHeadingDegrees(Vector3 origin, Vector3 target){
        if(origin == null || target == null || !origin.isFinite() || !target.isFinite()){
            throw new IllegalArgumentException("Coordinates must be finite");
        }
        double x = target.x() - origin.x(), z = target.z() - origin.z();
        if(!Double.isFinite(x) || !Double.isFinite(z)){
            x = target.x() * 0.5D - origin.x() * 0.5D;
            z = target.z() * 0.5D - origin.z() * 0.5D;
        }
        if(x == 0.0D && z == 0.0D) return 0.0D;
        double degrees = Math.toDegrees(Math.atan2(x, z));
        if(degrees < 0.0D) degrees += 360.0D;
        return degrees == 0.0D || degrees >= 360.0D ? 0.0D : degrees;
    }

    // Resolve the nearest world cardinal from a heading in radians measured from +Z toward +X
    public static String horizontalCardinal(double radians){
        if(!Double.isFinite(radians)) throw new IllegalArgumentException("A heading must be finite");
        int sector = (int)Math.floor(radians % (2 * Math.PI) / (Math.PI / 4) + 0.5D);
        return HORIZONTAL_CARDINALS[Math.floorMod(sector, HORIZONTAL_CARDINALS.length)];
    }

    // Project a rotation's +Z forward direction onto the world X/Z plane
    public static String horizontalCardinal(Quaternion rotation){
        if(rotation == null || !Double.isFinite(rotation.x()) || !Double.isFinite(rotation.y())
                || !Double.isFinite(rotation.z()) || !Double.isFinite(rotation.w())
                || rotation.x() == 0 && rotation.y() == 0 && rotation.z() == 0 && rotation.w() == 0){
            throw new IllegalArgumentException("A heading requires a nonzero finite quaternion");
        }
        Vector3 forward = rotation.rotate(new Vector3(0, 0, 1));
        if(Math.hypot(forward.x(), forward.z()) < SINGULAR_EPSILON)
            throw new IllegalArgumentException("A vertical direction has no horizontal heading");
        return horizontalCardinal(Math.atan2(forward.x(), forward.z()));
    }

    // Rotate +Z toward the target using the shortest arc from the origin
    public static Quaternion rotationFromCoordinates(Vector3 origin, Vector3 target){
        if(origin == null || target == null || !origin.isFinite() || !target.isFinite()){
            throw new IllegalArgumentException("Coordinates must be finite");
        }
        Vector3 direction = target.subtract(origin);
        if(!direction.isFinite()){
            direction = target.scale(0.5D).subtract(origin.scale(0.5D));
        }
        double scale = Math.max(Math.max(Math.abs(direction.x()), Math.abs(direction.y())), Math.abs(direction.z()));
        if(scale == 0.0D) throw new IllegalArgumentException("Origin and target must be distinct");
        double x = direction.x() / scale, y = direction.y() / scale, z = direction.z() / scale;
        double across = Math.hypot(x, y);
        if(across == 0.0D) return z > 0.0D ? Quaternion.IDENTITY : new Quaternion(0, 1, 0, 0);
        return Quaternion.fromAxisAngle(new Vector3(-y / across, x / across, 0), Math.atan2(across, z));
    }

    // Compose YXZ Tait-Bryan yaw about Y, pitch about X and roll about Z, in radians
    public static Quaternion yawPitchRollToQuaternion(Vector3 radians){
        return Quaternion.fromAxisAngle(new Vector3(0, 1, 0), radians.x())
                .multiply(Quaternion.fromAxisAngle(new Vector3(1, 0, 0), radians.y()))
                .multiply(Quaternion.fromAxisAngle(new Vector3(0, 0, 1), radians.z())).normalized();
    }

    // Resolve YXZ Tait-Bryan yaw, pitch and roll at either pitch singularity
    public static Vector3 quaternionToYawPitchRoll(Quaternion val){
        Matrix3 m = val.rotationMatrix();
        double across = Math.hypot(m.m10(), m.m11());
        double pitch = Math.atan2(-m.m12(), across);
        return across < SINGULAR_EPSILON
                ? new Vector3(Math.atan2(-m.m20(), m.m00()), pitch, 0)
                : new Vector3(Math.atan2(m.m02(), m.m22()), pitch, Math.atan2(m.m10(), m.m11()));
    }

    // Compose proper ZYZ Euler alpha, beta and gamma in radians
    public static Quaternion eulerZyzToQuaternion(Vector3 radians){
        double a = radians.x() / 2, b = radians.y() / 2, g = radians.z() / 2;
        return new Quaternion(-Math.sin(b) * Math.sin(a - g), Math.sin(b) * Math.cos(a - g),
                Math.cos(b) * Math.sin(a + g), Math.cos(b) * Math.cos(a + g)).normalized();
    }

    // Resolve ZYZ Euler angles with beta in [0, pi] and gamma zero at either singularity
    public static Vector3 quaternionToEulerZyz(Quaternion val){
        Matrix3 m = val.rotationMatrix();
        double across = Math.hypot(m.m02(), m.m12()), beta = Math.atan2(across, m.m22());
        if(across < SINGULAR_EPSILON){
            double sign = m.m22() < 0 ? -1 : 1;
            return new Vector3(Math.atan2(sign * m.m10(), sign * m.m00()), beta, 0);
        }
        return new Vector3(Math.atan2(m.m12(), m.m02()), beta, Math.atan2(m.m21(), -m.m20()));
    }

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
        Matrix3 m = value.rotationMatrix();
        double across = Math.hypot(m.m00(), m.m10());
        double pitch = Math.atan2(-m.m20(), across);
        return across < SINGULAR_EPSILON
                ? new Vector3(0, pitch, Math.atan2(-m.m01(), m.m11()))
                : new Vector3(Math.atan2(m.m21(), m.m22()), pitch, Math.atan2(m.m10(), m.m00()));
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
