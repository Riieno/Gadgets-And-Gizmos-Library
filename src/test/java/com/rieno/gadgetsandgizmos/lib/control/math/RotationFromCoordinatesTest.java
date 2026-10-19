package com.rieno.gadgetsandgizmos.lib.control.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RotationFromCoordinatesTest{
    private static final Vector3 FORWARD = new Vector3(0, 0, 1);

    @Test void cardinalTargetsPointForwardTowardTheTarget(){
        for(Vector3 target : new Vector3[]{FORWARD, new Vector3(0, 0, -1), new Vector3(1, 0, 0),
                new Vector3(-1, 0, 0), new Vector3(0, 1, 0), new Vector3(0, -1, 0)}){
            Quaternion rotation = RotationMath.rotationFromCoordinates(Vector3.ZERO, target);
            assertVector(target, rotation.rotate(FORWARD));
            assertEquals(1, rotation.magnitude(), 1.0E-12);
            assertEquals(Math.acos(target.z()), rotation.axisAngle().radians(), 1.0E-12);
        }
    }

    @Test void translationDoesNotChangeTheRotation(){
        Vector3 displacement = new Vector3(3, 4, -5);
        Vector3 origin = new Vector3(120, -50, 20);
        var rotation = RotationMath.rotationFromCoordinates(origin, origin.add(displacement));
        assertVector(displacement.normalized(), rotation.rotate(FORWARD));
        assertEquals(1, Math.abs(rotation.dot(RotationMath.rotationFromCoordinates(Vector3.ZERO, displacement))), 1.0E-12);
        assertEquals(Quaternion.IDENTITY, RotationMath.rotationFromCoordinates(new Vector3(1, 0, 0), new Vector3(1, 0, 1)));
    }

    @Test void directlyBehindUsesADeterministicYawAxis(){
        var rotation = RotationMath.rotationFromCoordinates(Vector3.ZERO, new Vector3(0, 0, -1));
        assertEquals(new Vector3(0, 1, 0), rotation.axisAngle().axis());
        assertEquals(Math.PI, rotation.axisAngle().radians());
    }

    @Test void nearParallelTargetsAndVerySmallDistancesRemainFinite(){
        for(Vector3 target : new Vector3[]{new Vector3(1.0E-14, 0, -1), new Vector3(0, 1.0E-14, 1),
                new Vector3(Double.MIN_VALUE, 0, 0)}){
            var rotation = RotationMath.rotationFromCoordinates(Vector3.ZERO, target);
            Vector3 unit = target.x() == Double.MIN_VALUE ? new Vector3(1, 0, 0) : target.normalized();
            assertVector(unit, rotation.rotate(FORWARD));
            assertTrue(Double.isFinite(rotation.axisAngle().radians()));
        }
    }

    @Test void finiteCoordinatesDoNotOverflowWhenTheirDifferenceDoes(){
        var rotation = RotationMath.rotationFromCoordinates(new Vector3(-Double.MAX_VALUE, 0, 0),
                new Vector3(Double.MAX_VALUE, 0, 0));
        assertVector(new Vector3(1, 0, 0), rotation.rotate(FORWARD));
        var diagonal = RotationMath.rotationFromCoordinates(Vector3.ZERO,
                new Vector3(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE));
        assertVector(new Vector3(1, 1, 1).normalized(), diagonal.rotate(FORWARD));
    }

    @Test void equalAndNonFiniteCoordinatesHaveNoDefinedDirection(){
        assertThrows(IllegalArgumentException.class, () -> RotationMath.rotationFromCoordinates(FORWARD, FORWARD));
        assertThrows(IllegalArgumentException.class, () -> RotationMath.rotationFromCoordinates(null, FORWARD));
        assertThrows(IllegalArgumentException.class, () -> RotationMath.rotationFromCoordinates(Vector3.ZERO, new Vector3(Double.NaN, 0, 1)));
        assertThrows(IllegalArgumentException.class, () -> RotationMath.rotationFromCoordinates(new Vector3(0, Double.POSITIVE_INFINITY, 0), FORWARD));
    }

    private static void assertVector(Vector3 expected, Vector3 actual){
        assertEquals(expected.x(), actual.x(), 1.0E-12);
        assertEquals(expected.y(), actual.y(), 1.0E-12);
        assertEquals(expected.z(), actual.z(), 1.0E-12);
    }
}
