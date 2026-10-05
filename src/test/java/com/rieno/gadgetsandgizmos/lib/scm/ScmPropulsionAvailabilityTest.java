package com.rieno.gadgetsandgizmos.lib.scm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScmPropulsionAvailabilityTest{
    @Test
    void pressureAppliesOnlyWhenTheProviderUsesIt(){
        assertEquals(25.0D, ScmPropulsionAvailability.capacity(
                100.0D, 0.5D, false, 0.5D), 1.0E-8D);
        assertEquals(50.0D, ScmPropulsionAvailability.capacity(
                100.0D, 0.5D, true, 0.5D), 1.0E-8D);
        assertEquals(100.0D, ScmPropulsionAvailability.capacity(
                100.0D, 0.5D, true, Double.NaN), 1.0E-8D);
        assertEquals(0.0D, ScmPropulsionAvailability.capacity(
                100.0D, 1.0D, true, 0.0D), 1.0E-8D);
    }
}
