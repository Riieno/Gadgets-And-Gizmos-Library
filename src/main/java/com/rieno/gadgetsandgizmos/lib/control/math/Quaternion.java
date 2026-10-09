package com.rieno.gadgetsandgizmos.lib.control.math;

// Provide immutable quaternion operations for controller rotation math
public record Quaternion(double x, double y, double z, double w) {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Quaternion IDENTITY = new Quaternion(0.0D, 0.0D, 0.0D, 1.0D);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the normalized
    public Quaternion normalized() {
        double scale = Math.max(Math.max(Math.abs(x), Math.abs(y)), Math.max(Math.abs(z), Math.abs(w)));
        if(!Double.isFinite(scale) || scale == 0){
            return IDENTITY;
        }
        double sx = x / scale, sy = y / scale, sz = z / scale, sw = w / scale;
        double length = Math.sqrt(sx * sx + sy * sy + sz * sz + sw * sw);
        return new Quaternion(sx / length, sy / length, sz / length, sw / length);
    }

    public double magnitude(){
        return Math.hypot(Math.hypot(x, y), Math.hypot(z, w));
    }

    public double dot(Quaternion other){
        return x * other.x + y * other.y + z * other.z + w * other.w;
    }

    public Quaternion add(Quaternion other){
        return new Quaternion(x + other.x, y + other.y, z + other.z, w + other.w);
    }

    public Quaternion subtract(Quaternion other){
        return add(other.scale(-1));
    }

    public Quaternion scale(double val){
        return new Quaternion(x * val, y * val, z * val, w * val);
    }

    // Hamilton product: the other rotation acts first
    public Quaternion multiply(Quaternion other){
        return new Quaternion(w * other.x + x * other.w + y * other.z - z * other.y,
                w * other.y - x * other.z + y * other.w + z * other.x,
                w * other.z + x * other.y - y * other.x + z * other.w,
                w * other.w - x * other.x - y * other.y - z * other.z);
    }

    public Quaternion conjugate(){
        return new Quaternion(-x, -y, -z, w);
    }

    public Quaternion inverse(){
        double scale = Math.max(Math.max(Math.abs(x), Math.abs(y)), Math.max(Math.abs(z), Math.abs(w)));
        if(!Double.isFinite(scale) || scale == 0) throw new IllegalArgumentException("Quaternion has no inverse");
        Quaternion n = new Quaternion(x / scale, y / scale, z / scale, w / scale);
        return n.conjugate().scale((1 / n.dot(n)) / scale);
    }

    public Vector3 rotate(Vector3 val){
        Quaternion q = normalized();
        Vector3 axis = new Vector3(q.x, q.y, q.z);
        Vector3 cross = axis.cross(val).scale(2);
        return val.add(cross.scale(q.w)).add(axis.cross(cross));
    }

    public Matrix3 rotationMatrix(){
        Quaternion q = normalized();
        return new Matrix3(1 - 2 * (q.y * q.y + q.z * q.z), 2 * (q.x * q.y - q.z * q.w), 2 * (q.x * q.z + q.y * q.w),
                2 * (q.x * q.y + q.z * q.w), 1 - 2 * (q.x * q.x + q.z * q.z), 2 * (q.y * q.z - q.x * q.w),
                2 * (q.x * q.z - q.y * q.w), 2 * (q.y * q.z + q.x * q.w), 1 - 2 * (q.x * q.x + q.y * q.y));
    }

    // Accept proper rotation matrices, rejecting scale, shear and reflection
    public static Quaternion fromRotationMatrix(Matrix3 m){
        Matrix3 error = m.transpose().multiply(m).subtract(Matrix3.IDENTITY);
        if(!Double.isFinite(error.maxAbs()) || error.maxAbs() > 1.0E-8D || Math.abs(m.determinant() - 1) > 1.0E-8D){
            throw new IllegalArgumentException("Matrix must be a proper orthonormal rotation");
        }
        double trace = m.m00() + m.m11() + m.m22();
        if(trace > 0){
            double s = Math.sqrt(trace + 1) * 2;
            return new Quaternion((m.m21() - m.m12()) / s, (m.m02() - m.m20()) / s,
                    (m.m10() - m.m01()) / s, s / 4).normalized();
        }
        if(m.m00() > m.m11() && m.m00() > m.m22()){
            double s = Math.sqrt(1 + m.m00() - m.m11() - m.m22()) * 2;
            return new Quaternion(s / 4, (m.m01() + m.m10()) / s, (m.m02() + m.m20()) / s,
                    (m.m21() - m.m12()) / s).normalized();
        }
        if(m.m11() > m.m22()){
            double s = Math.sqrt(1 + m.m11() - m.m00() - m.m22()) * 2;
            return new Quaternion((m.m01() + m.m10()) / s, s / 4, (m.m12() + m.m21()) / s,
                    (m.m02() - m.m20()) / s).normalized();
        }
        double s = Math.sqrt(1 + m.m22() - m.m00() - m.m11()) * 2;
        return new Quaternion((m.m02() + m.m20()) / s, (m.m12() + m.m21()) / s, s / 4,
                (m.m10() - m.m01()) / s).normalized();
    }

    public static Quaternion fromAxisAngle(Vector3 axis, double radians){
        if(!Double.isFinite(radians) || !axis.isFinite()) throw new IllegalArgumentException("Axis and angle must be finite");
        Vector3 unit = axis.normalized();
        if(unit.equals(Vector3.ZERO)) return IDENTITY;
        double sin = Math.sin(radians / 2);
        return new Quaternion(unit.x() * sin, unit.y() * sin, unit.z() * sin, Math.cos(radians / 2));
    }

    public record AxisAngle(Vector3 axis, double radians){}

    public AxisAngle axisAngle(){
        Quaternion q = normalized();
        if(q.w < 0) q = q.scale(-1);
        double sin = Math.hypot(Math.hypot(q.x, q.y), q.z);
        return sin < 1.0E-12D ? new AxisAngle(new Vector3(1, 0, 0), 0)
                : new AxisAngle(new Vector3(q.x / sin, q.y / sin, q.z / sin), 2 * Math.atan2(sin, q.w));
    }

    // Interpolate unit rotations along the shortest arc; permit extrapolation
    public Quaternion slerp(Quaternion other, double amount){
        if(!Double.isFinite(amount)) throw new IllegalArgumentException("Interpolation amount must be finite");
        Quaternion a = normalized(), b = other.normalized();
        double dot = a.dot(b);
        if(dot < 0){ b = b.scale(-1); dot = -dot; }
        dot = Math.max(-1, Math.min(1, dot));
        if(dot > 0.9995) return a.scale(1 - amount).add(b.scale(amount)).normalized();
        double theta = Math.acos(dot), sin = Math.sin(theta);
        return a.scale(Math.sin((1 - amount) * theta) / sin)
                .add(b.scale(Math.sin(amount * theta) / sin)).normalized();
    }
}
