package com.rieno.gadgetsandgizmos.lib.scm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Verify collision authority stays with opted-in autonomous movement
class ScmCollisionAuthorityTest{
    @Test void navigationRequiresTheAvoidanceFlag(){
        for(String type : new String[]{"ship_navigate", "ship_follow", "ship_climb"}){
            assertFalse(ScmCommandRouting.allowsCollisionAvoidance(type, false, false, false));
            assertTrue(ScmCommandRouting.allowsCollisionAvoidance(type, true, false, false));
            assertTrue(ScmCommandRouting.allowsCollisionAvoidance(type, true, true, false));
        }
    }

    @Test void manualCommandsNeverAcquireAvoidanceEvenWithAnAuthoredFlag(){
        for(String type : new String[]{"ship_forward", "ship_accelerate", "ship_backward", "ship_reverse",
                "ship_strafe", "ship_strafe_left", "ship_strafe_right", "ship_ascend", "ship_descend",
                "ship_jump", "ship_crouch", "ship_yaw", "ship_yaw_left", "ship_yaw_right", "ship_pan",
                "ship_pitch", "ship_pitch_up", "ship_pitch_down", "ship_tilt", "ship_roll",
                "ship_roll_left", "ship_roll_right", "ship_decelerate", "ship_brake", "ship_stop"}){
            assertTrue(ScmCommandRouting.isManualControl(type), type);
            assertFalse(ScmCommandRouting.allowsCollisionAvoidance(type, true, true, false), type);
        }
        for(String type : new String[]{"ship_hover", "ship_stabilize", "ship_face", "unknown"}){
            assertFalse(ScmCommandRouting.allowsCollisionAvoidance(type, true, true, false), type);
        }
        assertFalse(ScmCommandRouting.allowsCollisionAvoidance(null, true, true, false));
    }

    @Test void graphAndScheduleDockingKeepTheirCollisionAuthority(){
        assertTrue(ScmCommandRouting.allowsCollisionAvoidance("ship_dock", true, false, false));
        assertFalse(ScmCommandRouting.allowsCollisionAvoidance("ship_dock", false, true, false));
        assertTrue(ScmCommandRouting.allowsCollisionAvoidance("ship_dock", true, true, false));
    }

    @Test void stopMarkersAndZeroInputsDoNotTakeManualAuthority(){
        assertFalse(ScmCommandRouting.isActiveManualControl("ship_stop", 0, 1));
        assertFalse(ScmCommandRouting.isActiveManualControl("ship_jump", 0, 0));
        assertFalse(ScmCommandRouting.isActiveManualControl("ship_forward", 0, 0));
        assertFalse(ScmCommandRouting.isActiveManualControl("ship_forward", Double.NaN, 0));
        assertTrue(ScmCommandRouting.isActiveManualControl("ship_forward", 0.25D, 0));
        assertTrue(ScmCommandRouting.isActiveManualControl("ship_brake", 0, 0.5D));
    }

    @Test void manualInputSuspendsAutonomousAvoidance(){
        for(String type : new String[]{"ship_navigate", "ship_follow", "ship_climb", "ship_dock"}){
            assertFalse(ScmCommandRouting.allowsCollisionAvoidance(type, true, true, true), type);
            assertFalse(ScmCommandRouting.isManualControl(type), type);
        }
        assertFalse(ScmCommandRouting.isManualControl("ship_hover"));
        assertFalse(ScmCommandRouting.isManualControl("ship_stabilize"));
        assertFalse(ScmCommandRouting.isManualControl(null));
    }
}
