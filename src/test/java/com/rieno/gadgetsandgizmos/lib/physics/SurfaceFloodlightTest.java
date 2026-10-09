package com.rieno.gadgetsandgizmos.lib.physics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SurfaceFloodlightTest{
    @Test void distantHitsCoverMoreAreaWithLessLight(){
        var near = SurfaceFloodlight.profile(1);
        var middle = SurfaceFloodlight.profile(20);
        var far = SurfaceFloodlight.profile(60);
        assertTrue(near.radius() < middle.radius());
        assertTrue(middle.radius() < far.radius());
        assertTrue(near.brightness() > middle.brightness());
        assertTrue(middle.brightness() > far.brightness());
        assertTrue(near.light() > far.light());
    }
    @Test void nonFiniteDistancesCannotProduceUnboundedLights(){
        var light = SurfaceFloodlight.profile(Double.NaN);
        assertTrue(Double.isFinite(light.radius()));
        assertTrue(light.radius() <= 12);
        assertTrue(light.light() >= 0 && light.light() <= 15);
    }
    @Test void brightCloseHitsDoNotIlluminateOutsideTheirSmallFootprint(){
        var near = SurfaceFloodlight.profile(0.5);
        assertTrue(near.intensity(0) > 0.99);
        assertEquals(0, near.intensity(near.radius()));
        assertEquals(0, near.intensity(1));
        assertTrue(SurfaceFloodlight.profile(30).intensity(2) > 0);
    }
}
