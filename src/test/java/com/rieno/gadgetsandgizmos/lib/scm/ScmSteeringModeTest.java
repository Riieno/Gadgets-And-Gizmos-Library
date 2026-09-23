package com.rieno.gadgetsandgizmos.lib.scm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScmSteeringModeTest {
    @Test
    void predictsAuthoredSteeringBeforeGenericFallbacks(){
        assertEquals(ScmSteeringMode.TANK,
                ScmSteeringMode.predict(true, 2, 2, 4, true));
        assertEquals(ScmSteeringMode.FOUR_WHEEL,
                ScmSteeringMode.predict(false, 2, 2, 4, true));
        assertEquals(ScmSteeringMode.FRONT_WHEEL,
                ScmSteeringMode.predict(false, 0, 0, 4, true));
        assertEquals(ScmSteeringMode.RUDDER,
                ScmSteeringMode.predict(false, 0, 0, 0, true));
    }

    @Test
    void phasesFrontAndRearAxlesOppositely(){
        assertEquals(0.75D, ScmSteeringMode.FOUR_WHEEL
                .wheelDemand(0.75D, 4.0D, -4.0D, 4.0D), 1.0E-8D);
        assertEquals(-0.75D, ScmSteeringMode.FOUR_WHEEL
                .wheelDemand(0.75D, -4.0D, -4.0D, 4.0D), 1.0E-8D);
        assertEquals(0.0D, ScmSteeringMode.FRONT_WHEEL
                .wheelDemand(0.75D, -4.0D, -4.0D, 4.0D), 1.0E-8D);
        assertEquals(-0.75D, ScmSteeringMode.REAR_WHEEL
                .wheelDemand(0.75D, -4.0D, -4.0D, 4.0D), 1.0E-8D);
    }

    @Test
    void tankCutsDriveOnlyDuringAnActiveTurn(){
        assertTrue(ScmSteeringMode.TANK.cutsLongitudinalPower(0.5D));
        assertFalse(ScmSteeringMode.TANK.cutsLongitudinalPower(0.0D));
        assertFalse(ScmSteeringMode.CUSTOM.cutsLongitudinalPower(0.5D));
    }

    @Test
    void planningAccountsForFourWheelAndTankTurningRadius(){
        double physical = Math.toRadians(32.0D);
        assertTrue(ScmSteeringMode.FOUR_WHEEL.planningSteeringRadians(physical) > physical);
        assertTrue(ScmSteeringMode.TANK.planningSteeringRadians(physical)
                > ScmSteeringMode.FOUR_WHEEL.planningSteeringRadians(physical));
        assertEquals(physical,
                ScmSteeringMode.FRONT_WHEEL.planningSteeringRadians(physical), 1.0E-8D);
    }
}
