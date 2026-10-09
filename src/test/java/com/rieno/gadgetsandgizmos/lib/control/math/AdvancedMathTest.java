package com.rieno.gadgetsandgizmos.lib.control.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AdvancedMathTest{
    @Test
    void matrixCompositionAndInverseUseColumnVectors(){
        Matrix3 a = new Matrix3(2, 1, 0, 0, 3, 1, 1, 0, 4);
        Matrix3 b = new Matrix3(1, 2, 3, 0, 1, 4, 5, 6, 0);
        Vector3 v = new Vector3(3, -2, 7);
        vector(a.transform(b.transform(v)), a.multiply(b).transform(v), 1.0E-12);
        vector(v, a.inverse().transform(a.transform(v)), 1.0E-12);
        assertEquals(25, a.determinant(), 1.0E-12);
        assertEquals(a, a.transpose().transpose());
        assertThrows(IllegalArgumentException.class, Matrix3.ZERO::inverse);
        Matrix3 tiny = Matrix3.IDENTITY.scale(1.0E-100);
        vector(v, tiny.inverse().transform(tiny.transform(v)), 1.0E-12);
    }

    @Test
    void quaternionAlgebraPreservesMagnitudeAndCompositionOrder(){
        Quaternion q = new Quaternion(1, 2, 3, 4);
        Quaternion product = q.multiply(q.conjugate());
        quaternion(new Quaternion(0, 0, 0, 30), product, 1.0E-12);
        quaternion(Quaternion.IDENTITY, q.multiply(q.inverse()), 1.0E-12);
        assertThrows(IllegalArgumentException.class, () -> new Quaternion(0, 0, 0, 0).inverse());
        Quaternion a = Quaternion.fromAxisAngle(new Vector3(1, 0, 0), Math.PI / 2);
        Quaternion b = Quaternion.fromAxisAngle(new Vector3(0, 0, 1), Math.PI / 2);
        Vector3 v = new Vector3(1, 2, 3);
        vector(a.rotate(b.rotate(v)), a.multiply(b).rotate(v), 1.0E-12);
        vector(a.rotate(v), a.rotationMatrix().transform(v), 1.0E-12);
        assertEquals(1, new Quaternion(1.0E300, -1.0E300, 1.0E300, 1.0E300).normalized().magnitude(), 1.0E-12);
        assertEquals(1, new Quaternion(1.0E-300, 0, 0, 1.0E-300).normalized().magnitude(), 1.0E-12);
    }

    @Test
    void matrixAxisAngleAndSlerpRoundTripRotations(){
        for(Vector3 axis : new Vector3[]{new Vector3(1, 0, 0), new Vector3(0, 1, 0), new Vector3(0, 0, 1), new Vector3(1, 2, 3)}){
            for(double angle : new double[]{0, 0.7, Math.PI, 5.4}){
                Quaternion q = Quaternion.fromAxisAngle(axis, angle);
                assertEquals(1, Math.abs(q.dot(Quaternion.fromRotationMatrix(q.rotationMatrix()))), 1.0E-12);
                Quaternion.AxisAngle aa = q.axisAngle();
                assertEquals(1, Math.abs(q.dot(Quaternion.fromAxisAngle(aa.axis(), aa.radians()))), 1.0E-12);
                assertEquals(1, Math.abs(q.dot(q.slerp(q.scale(-1), 0.3))), 1.0E-12);
            }
        }
        Quaternion half = Quaternion.IDENTITY.slerp(Quaternion.fromAxisAngle(new Vector3(0, 0, 1), Math.PI), 0.5);
        vector(new Vector3(0, 1, 0), half.rotate(new Vector3(1, 0, 0)), 1.0E-12);
        assertThrows(IllegalArgumentException.class, () -> Quaternion.fromRotationMatrix(Matrix3.IDENTITY.scale(2)));
        assertThrows(IllegalArgumentException.class, () -> Quaternion.fromRotationMatrix(Matrix3.diagonal(new Vector3(-1, 1, 1))));
    }

    @Test
    void pointsAndTensorsRoundTripWithOffsetAndRotation(){
        Quaternion q = Quaternion.fromAxisAngle(new Vector3(0, 0, 1), Math.PI / 2);
        Vector3 origin = new Vector3(10, -3, 5), local = new Vector3(1, 2, 3);
        Vector3 world = SpatialTransforms.localToWorld(local, origin, q);
        vector(new Vector3(8, -2, 8), world, 1.0E-12);
        vector(local, SpatialTransforms.worldToLocal(world, origin, q), 1.0E-12);
        Matrix3 tensor = Matrix3.diagonal(new Vector3(2, 5, 7));
        Matrix3 rotated = SpatialTransforms.tensorToWorld(tensor, q);
        vector(new Vector3(5, 2, 7), new Vector3(rotated.m00(), rotated.m11(), rotated.m22()), 1.0E-12);
        assertEquals(0, SpatialTransforms.tensorToLocal(rotated, q).subtract(tensor).maxAbs(), 1.0E-12);
    }

    @Test
    void linearPredictionMatchesConstantJerkPolynomial(){
        var s = new Kinematics.LinearState(new Vector3(1, 0, 0), new Vector3(2, 0, 0), new Vector3(3, 0, 0));
        var res = Kinematics.predict(s, new Vector3(6, 0, 0), 2);
        vector(new Vector3(19, 0, 0), res.position(), 1.0E-12);
        vector(new Vector3(20, 0, 0), res.velocity(), 1.0E-12);
        vector(new Vector3(15, 0, 0), res.acceleration(), 1.0E-12);
        assertEquals(s, Kinematics.predict(s, Vector3.ZERO, 0));
        assertThrows(IllegalArgumentException.class, () -> Kinematics.predict(s, Vector3.ZERO, -1));
    }

    @Test
    void rigidPredictionMatchesAnalyticIsotropicDrag(){
        var s = rigid(new Vector3(4, 0, 0), Vector3.ZERO, Vector3.ZERO);
        var res = Kinematics.predict(s, Vector3.ZERO, Vector3.ZERO, Matrix3.IDENTITY.scale(2), Matrix3.ZERO, 1, 64);
        vector(new Vector3(4 * Math.exp(-2), 0, 0), res.velocity(), 1.0E-7);
        vector(new Vector3(2 * (1 - Math.exp(-2)), 0, 0), res.position(), 1.0E-7);
        assertEquals(1, res.orientation().magnitude(), 1.0E-12);
    }

    @Test
    void rigidPredictionCouplesRotatingThrustToTranslation(){
        double w = 1.2, t = 2;
        var s = rigid(Vector3.ZERO, new Vector3(1, 0, 0), new Vector3(0, 0, w));
        var res = Kinematics.predict(s, Vector3.ZERO, Vector3.ZERO, Matrix3.ZERO, Matrix3.ZERO, t, 128);
        vector(new Vector3(Math.sin(w * t) / w, (1 - Math.cos(w * t)) / w, 0), res.velocity(), 1.0E-8);
        vector(new Vector3((1 - Math.cos(w * t)) / (w * w), t / w - Math.sin(w * t) / (w * w), 0), res.position(), 1.0E-8);
        Quaternion expected = Quaternion.fromAxisAngle(new Vector3(0, 0, 1), w * t);
        assertEquals(1, Math.abs(expected.dot(res.orientation())), 1.0E-10);
    }

    @Test
    void anisotropicDragUsesBodyAxesAndAngularDamping(){
        var s = new Kinematics.RigidState(Vector3.ZERO, new Vector3(4, 3, 0), Vector3.ZERO,
                Quaternion.fromAxisAngle(new Vector3(0, 0, 1), Math.PI / 2), Vector3.ZERO, Vector3.ZERO);
        var res = Kinematics.predict(s, Vector3.ZERO, Vector3.ZERO, Matrix3.diagonal(new Vector3(2, 1, 0)), Matrix3.ZERO, 1, 64);
        vector(new Vector3(4 * Math.exp(-1), 3 * Math.exp(-2), 0), res.velocity(), 1.0E-7);
        var turning = rigid(Vector3.ZERO, Vector3.ZERO, new Vector3(0, 0, 2));
        var damped = Kinematics.predict(turning, Vector3.ZERO, Vector3.ZERO, Matrix3.ZERO, Matrix3.IDENTITY, 1, 64);
        vector(new Vector3(0, 0, 2 * Math.exp(-1)), damped.angularVelocity(), 1.0E-8);
    }

    @Test
    void rigidAndLinearJerkAgreeWithoutRotationOrDrag(){
        Vector3 jerk = new Vector3(2, 3, -1);
        var s = rigid(new Vector3(1, 2, 3), new Vector3(-2, 1, 4), Vector3.ZERO);
        var a = Kinematics.predict(new Kinematics.LinearState(s.position(), s.velocity(), s.acceleration()), jerk, 2);
        var b = Kinematics.predict(s, jerk, Vector3.ZERO, Matrix3.ZERO, Matrix3.ZERO, 2, 8);
        vector(a.position(), b.position(), 1.0E-12);
        vector(a.velocity(), b.velocity(), 1.0E-12);
        vector(a.acceleration(), b.acceleration(), 1.0E-12);
        assertThrows(IllegalArgumentException.class, () -> Kinematics.predict(s, jerk, Vector3.ZERO, Matrix3.ZERO, Matrix3.ZERO, 1, 0));
    }

    private static Kinematics.RigidState rigid(Vector3 velocity, Vector3 acceleration, Vector3 angularVelocity){
        return new Kinematics.RigidState(Vector3.ZERO, velocity, acceleration, Quaternion.IDENTITY, angularVelocity, Vector3.ZERO);
    }

    private static void vector(Vector3 expected, Vector3 actual, double tolerance){
        assertEquals(expected.x(), actual.x(), tolerance);
        assertEquals(expected.y(), actual.y(), tolerance);
        assertEquals(expected.z(), actual.z(), tolerance);
    }

    private static void quaternion(Quaternion expected, Quaternion actual, double tolerance){
        assertEquals(expected.x(), actual.x(), tolerance);
        assertEquals(expected.y(), actual.y(), tolerance);
        assertEquals(expected.z(), actual.z(), tolerance);
        assertEquals(expected.w(), actual.w(), tolerance);
    }
}
