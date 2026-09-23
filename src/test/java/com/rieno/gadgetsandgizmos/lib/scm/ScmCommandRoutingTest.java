package com.rieno.gadgetsandgizmos.lib.scm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScmCommandRoutingTest {
    @Test
    void autonomousAndDirectTravelCommandsRetainSpeedControl() {
        assertTrue(ScmCommandRouting.requiresAccelerationControl("ship_navigate"));
        assertTrue(ScmCommandRouting.requiresAccelerationControl("ship_follow"));
        assertTrue(ScmCommandRouting.requiresAccelerationControl("ship_climb"));
        assertTrue(ScmCommandRouting.requiresAccelerationControl("ship_forward"));
        assertTrue(ScmCommandRouting.requiresAccelerationControl("ship_ascend"));
        assertTrue(ScmCommandRouting.requiresAccelerationControl("ship_jump"));
    }

    @Test
    void rotationOnlyCommandsDoNotEnableSpeedControl() {
        assertFalse(ScmCommandRouting.requiresAccelerationControl("ship_yaw"));
        assertFalse(ScmCommandRouting.requiresAccelerationControl("ship_crouch"));
        assertFalse(ScmCommandRouting.requiresAccelerationControl("ship_face"));
        assertFalse(ScmCommandRouting.requiresAccelerationControl(null));
    }
}
