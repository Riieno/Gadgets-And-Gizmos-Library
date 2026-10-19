package com.rieno.gadgetsandgizmos.lib.scm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Exercise host availability without losing saved vehicle and steering identifiers
class ScmFeatureAvailabilityTest{
    private boolean groundSea;
    private boolean plane;

    @BeforeEach void retainAvailability(){
        groundSea = ScmBuiltinControlModes.isGroundSeaEnabled();
        plane = ScmBuiltinControlModes.isPlaneEnabled();
    }

    @AfterEach void restoreAvailability(){
        ScmBuiltinControlModes.setGroundSeaEnabled(groundSea);
        ScmBuiltinControlModes.setPlaneEnabled(plane);
    }

    @Test void disabledGroundControlKeepsRudderAndSavedSteering(){
        ScmBuiltinControlModes.setGroundSeaEnabled(false);
        assertFalse(ScmControlModeRegistry.serializedIds().contains("car"));
        assertEquals(ScmBuiltinControlModes.AIRSHIP_ID, ScmControlModeRegistry.resolve("car").id());
        assertTrue(ScmVehicleClassifier.isSelection("car"));
        assertFalse(ScmVehicleClassifier.isAvailableSelection("car"));
        assertEquals(List.of(ScmSteeringMode.RUDDER, ScmSteeringMode.CUSTOM, ScmSteeringMode.AUTO),
                ScmSteeringMode.availableSelections());
        for(ScmSteeringMode mode : List.of(ScmSteeringMode.FRONT_WHEEL, ScmSteeringMode.REAR_WHEEL,
                ScmSteeringMode.FOUR_WHEEL, ScmSteeringMode.TANK)){
            assertTrue(ScmSteeringMode.isSelection(mode.id()));
            assertSame(mode, ScmSteeringMode.fromId(mode.id()));
            assertFalse(ScmSteeringMode.isAvailableSelection(mode.id()));
            assertSame(ScmSteeringMode.RUDDER, mode.availableOrFallback(ScmSteeringMode.RUDDER));
            assertSame(ScmSteeringMode.CUSTOM, mode.availableOrFallback(ScmSteeringMode.TANK));
        }
    }

    @Test void automaticDetectionRespectsIndependentVehicleGates(){
        for(boolean ground : new boolean[]{false, true}){
            for(boolean aircraft : new boolean[]{false, true}){
                ScmBuiltinControlModes.setGroundSeaEnabled(ground);
                ScmBuiltinControlModes.setPlaneEnabled(aircraft);
                assertEquals(ground ? "car" : "airship", ScmVehicleClassifier.suggestAvailable(true, false));
                assertEquals(aircraft ? "plane" : "airship", ScmVehicleClassifier.suggestAvailable(true, true));
                assertEquals("airship", ScmVehicleClassifier.suggestAvailable(false, false));
                assertTrue(ScmSteeringMode.RUDDER.isAvailable());
                assertEquals(ground, ScmSteeringMode.TANK.isAvailable());
            }
        }
    }

    @Test void reenabledModesResolveTheirOriginalIds(){
        ScmBuiltinControlModes.setGroundSeaEnabled(false);
        ScmBuiltinControlModes.setPlaneEnabled(false);
        assertTrue(ScmVehicleClassifier.isSelection("plane"));
        assertFalse(ScmVehicleClassifier.isAvailableSelection("plane"));
        assertEquals(ScmBuiltinControlModes.AIRSHIP_ID, ScmControlModeRegistry.resolve("plane").id());
        ScmBuiltinControlModes.setGroundSeaEnabled(true);
        ScmBuiltinControlModes.setPlaneEnabled(true);
        assertEquals(ScmBuiltinControlModes.GROUND_SEA_ID, ScmControlModeRegistry.resolve("car").id());
        assertEquals(ScmBuiltinControlModes.PLANE_ID, ScmControlModeRegistry.resolve("plane").id());
        assertTrue(ScmSteeringMode.availableSelections().contains(ScmSteeringMode.FRONT_WHEEL));
        assertEquals("plane", ScmVehicleClassifier.suggestAvailable(true, true));
    }
}
