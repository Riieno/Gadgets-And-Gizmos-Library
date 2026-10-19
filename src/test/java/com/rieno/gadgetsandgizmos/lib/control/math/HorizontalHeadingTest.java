package com.rieno.gadgetsandgizmos.lib.control.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HorizontalHeadingTest{
    @Test void cardinalHeadingsCoverAllFourQuadrants(){
        assertEquals(0, heading(0, 1));assertEquals(90, heading(1, 0));
        assertEquals(180, heading(0, -1));assertEquals(270, heading(-1, 0));
        assertEquals(45, heading(1, 1));assertEquals(135, heading(1, -1));
        assertEquals(225, heading(-1, -1));assertEquals(315, heading(-1, 1));
    }

    @Test void aCompleteCircleContinuesPast180AndWrapsOnlyAt360(){
        Vector3 origin = new Vector3(12, -59, -78);
        double previous = 0;
        for(int step = 1; step <= 1440; step++){
            double expected = (step * 0.5) % 360;
            double radians = Math.toRadians(expected);
            Vector3 target = origin.add(new Vector3(Math.sin(radians) * 16, step % 2 == 0 ? 100 : -100, Math.cos(radians) * 16));
            double actual = RotationMath.horizontalHeadingDegrees(origin, target);
            assertTrue(actual >= 0 && actual < 360);
            assertEquals(expected, actual, 1.0E-10);
            double change = (actual - previous + 540) % 360 - 180;
            assertEquals(0.5, change, 1.0E-10);previous = actual;
        }
    }

    @Test void heightAndTranslationDoNotChangeTheHorizontalHeading(){
        for(double height : new double[]{-1000, 0, 1000}){
            assertEquals(270, RotationMath.horizontalHeadingDegrees(new Vector3(12, -59, -78), new Vector3(4, height, -78)));
        }
    }

    @Test void directlyAboveBelowAndCoincidentReturnZeroHeading(){
        Vector3 origin = new Vector3(12, -59, -78);
        assertEquals(0, RotationMath.horizontalHeadingDegrees(origin, origin));
        assertEquals(0, RotationMath.horizontalHeadingDegrees(origin, new Vector3(12, 1000, -78)));
        assertEquals(0, RotationMath.horizontalHeadingDegrees(origin, new Vector3(12, -1000, -78)));
    }

    @Test void extremeFiniteCoordinatesAndWrapRoundingStayWithinRange(){
        assertEquals(135, RotationMath.horizontalHeadingDegrees(new Vector3(-Double.MAX_VALUE, 0, Double.MAX_VALUE),
                new Vector3(Double.MAX_VALUE, 0, -Double.MAX_VALUE)), 1.0E-10);
        assertEquals(90, heading(Double.MIN_VALUE, 0));
        assertEquals(0, heading(-Double.MIN_VALUE, Double.MAX_VALUE));
        assertEquals(Double.doubleToLongBits(0.0), Double.doubleToLongBits(heading(-0.0, 1)));
        double nearZero = heading(-1.0E-12, 1);
        assertTrue(nearZero > 359.9 && nearZero < 360);
    }

    @Test void invalidCoordinatesAreRejected(){
        assertThrows(IllegalArgumentException.class, () -> RotationMath.horizontalHeadingDegrees(null, Vector3.ZERO));
        assertThrows(IllegalArgumentException.class, () -> RotationMath.horizontalHeadingDegrees(Vector3.ZERO, null));
        assertThrows(IllegalArgumentException.class, () -> heading(Double.NaN, 1));
        assertThrows(IllegalArgumentException.class, () -> heading(1, Double.POSITIVE_INFINITY));
    }

    private static double heading(double x, double z){
        return RotationMath.horizontalHeadingDegrees(Vector3.ZERO, new Vector3(x, 0, z));
    }
}
