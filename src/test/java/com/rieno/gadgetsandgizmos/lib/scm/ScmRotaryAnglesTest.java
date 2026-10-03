package com.rieno.gadgetsandgizmos.lib.scm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScmRotaryAnglesTest {
    @Test
    void preservesThePositivePiEndpoint(){
        assertEquals(Math.PI, ScmRotaryAngles.wrap(Math.PI));
        assertEquals(Math.PI, ScmRotaryAngles.wrap(-Math.PI));
    }

    @Test
    void unwrapsBothDirectionsAcrossTheNativeSeam(){
        assertEquals(Math.PI + 0.02D, ScmRotaryAngles.unwrapNear(-Math.PI + 0.02D,
                Math.PI - 0.02D), 1.0E-10D);
        assertEquals(-Math.PI - 0.02D, ScmRotaryAngles.unwrapNear(Math.PI - 0.02D,
                -Math.PI + 0.02D), 1.0E-10D);
    }

    @Test
    void keepsAWrapCrossingArcContinuousAndClampsAtItsRealStops(){
        ScmRotaryAngles.Interval limits = ScmRotaryAngles.interval(
                Math.toRadians(170), Math.toRadians(20), Math.toRadians(179));
        assertEquals(Math.toRadians(170), limits.min(), 1.0E-10D);
        assertEquals(Math.toRadians(190), limits.max(), 1.0E-10D);
        assertEquals(Math.toRadians(181), limits.clamp(Math.toRadians(-179)), 1.0E-10D);
        assertEquals(Math.toRadians(190), limits.clamp(Math.toRadians(-160)), 1.0E-10D);
    }
}
