package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScmMotionDampingTest{
    @Test void brakingSettlesLinearAndAngularMotionWithoutReversingIt(){
        Vec3 velocity = new Vec3(20, -8, 5);
        Vec3 rotation = new Vec3(-2, 4, 1);
        for(int tick = 0; tick < 120; tick++){
            var demand = ScmMotionDamping.stop(velocity, rotation, 1);
            assertTrue(demand.acceleration().dot(velocity) <= 0);
            assertTrue(demand.angularAcceleration().dot(rotation) <= 0);
            Vec3 next = velocity.add(demand.acceleration().scale(0.05));
            Vec3 nextRotation = rotation.add(demand.angularAcceleration().scale(0.05));
            assertTrue(next.dot(velocity) >= 0);
            assertTrue(nextRotation.dot(rotation) >= 0);
            velocity = next;
            rotation = nextRotation;
        }
        assertTrue(velocity.length() < 0.001);
        assertTrue(rotation.length() < 0.001);
    }

    @Test void linearBrakingDoesNotRequireOrDampAngularMotion(){
        Vec3 velocity = new Vec3(3, -2, 1);
        Vec3 acceleration = ScmMotionDamping.stopLinear(velocity, 1);
        assertTrue(acceleration.dot(velocity) < 0);
        assertEquals(0, acceleration.distanceTo(velocity.scale(-2)), 1e-9);
        assertEquals(0, ScmMotionDamping.stopLinear(velocity, 0).length(), 1e-9);
    }

    @Test void tiltedYawHoldPreservesPitchAndRoll(){
        Vec3 up = new Vec3(1, 1, 0).normalize();
        Vec3 pitchRoll = new Vec3(1, -1, 3);
        Vec3 rate = pitchRoll.add(up.scale(-4));
        Vec3 correction = ScmMotionDamping.holdYaw(rate, up.scale(7), 1);
        assertEquals(8, correction.dot(up), 1e-9);
        assertEquals(0, correction.dot(pitchRoll), 1e-9);
        assertEquals(0, ScmMotionDamping.holdYaw(pitchRoll, up, 1).length(), 1e-9);
    }

    @Test void yawBrakingMeasuresOnlyYawAndNeverRestoresAStationaryHeading(){
        Vec3 up = new Vec3(1, 1, 0).normalize();
        Vec3 rate = new Vec3(1, -1, 3).add(up.scale(2));
        assertEquals(2, ScmMotionDamping.yawRate(rate, up.scale(7)), 1e-9);
        assertEquals(-4, ScmMotionDamping.holdYaw(rate, up, 1).dot(up), 1e-9);
        assertEquals(0, ScmMotionDamping.holdYaw(Vec3.ZERO, up, 1).length(), 1e-9);
        assertEquals(0, ScmMotionDamping.yawRate(rate, Vec3.ZERO), 1e-9);
        assertEquals(0, ScmMotionDamping.yawRate(new Vec3(Double.NaN, 1, Double.POSITIVE_INFINITY), new Vec3(1, 0, 0)), 1e-9);
    }

    @Test void invalidSignalsCannotInjectInvalidForces(){
        assertEquals(0, ScmMotionDamping.stop(Vec3.ZERO, Vec3.ZERO, Double.NaN).acceleration().length(), 1e-9);
        assertEquals(Vec3.ZERO, ScmMotionDamping.holdYaw(new Vec3(1, 2, 3), Vec3.ZERO, 1));
        assertEquals(0, ScmMotionDamping.stop(new Vec3(1, 2, 3), Vec3.ZERO, 0).acceleration().length(), 1e-9);
        assertEquals(0, new Vec3(-1, 0, 0).distanceTo(ScmMotionDamping.stop(new Vec3(1, 0, 0), Vec3.ZERO, 0.5).acceleration()), 1e-9);
        assertEquals(0, new Vec3(0, -4, 0).distanceTo(ScmMotionDamping.stop(new Vec3(Double.NaN, 2, Double.POSITIVE_INFINITY), Vec3.ZERO, 2).acceleration()), 1e-9);
    }

    @Test void capturedHeadingReturnsAfterAnExternalYawDisturbance(){
        double angle = Math.toRadians(35);
        double rate = 0;
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 target = new Vec3(0, 0, -1);
        for(int tick = 0; tick < 240; tick++){
            Vec3 forward = new Vec3(-Math.sin(angle), 0, -Math.cos(angle));
            Vec3 correction = ScmMotionDamping.holdYaw(new Vec3(0, rate, 0), up, forward, target, 1);
            assertEquals(0, correction.x, 1e-9);
            assertEquals(0, correction.z, 1e-9);
            rate += correction.y * 0.05;
            angle += rate * 0.05;
            if(tick == 120) rate += 0.5;
        }
        assertEquals(0, angle, 0.001);
        assertEquals(0, rate, 0.001);
    }

    @Test void headingErrorWrapsAndIgnoresPitchAndRollComponents(){
        Vec3 up = new Vec3(0, 1, 0);
        double from = Math.toRadians(179), to = Math.toRadians(-179);
        assertEquals(Math.toRadians(2), ScmMotionDamping.yawError(
                new Vec3(-Math.sin(from), 0.4, -Math.cos(from)),
                new Vec3(-Math.sin(to), -0.5, -Math.cos(to)), up), 1e-9);
        assertEquals(0, ScmMotionDamping.holdYaw(new Vec3(0, 4, 0), up,
                new Vec3(0, 0, -1), new Vec3(-1, 0, 0), 0).length(), 1e-9);
        assertEquals(0, ScmControlInputs.strength(-1));
        assertEquals(0, ScmControlInputs.strength(0));
        assertEquals(1, ScmControlInputs.strength(2));
        assertEquals(0, ScmControlInputs.strength(Double.NaN));
    }

    @Test void completedMotionStopsDoNotModifyImmutableActionSets(){
        var original = java.util.Set.of("ship_brake", "ship_forward", "ship_ascend");
        assertEquals(java.util.Set.of("ship_forward", "ship_ascend"),
                ScmCommandRouting.activeActions("ship_brake", true, original));
        assertTrue(original.contains("ship_brake"));
        assertEquals(original, ScmCommandRouting.activeActions("ship_brake", false, original));
        assertEquals(java.util.Set.of("ship_hold_yaw", "ship_yaw", "ship_yaw_left", "ship_yaw_right"), ScmCommandRouting.activeActions("ship_hold_yaw", true,
                java.util.Set.of("ship_hold_yaw", "ship_yaw", "ship_yaw_left", "ship_yaw_right")));
        assertEquals(java.util.Set.of(), ScmCommandRouting.activeActions("ship_decelerate", true,
                java.util.Set.of("ship_decelerate")));
        assertEquals(java.util.Set.of("ship_hover", "ship_ascend"), ScmCommandRouting.activeActions(
                "ship_hover", true, java.util.Set.of("ship_hover", "ship_ascend")));
    }

    @Test void completedMotionStopsReleaseManualPriorityButOtherInputsStayLive(){
        for(String type : new String[]{"ship_brake", "ship_hold_yaw", "ship_decelerate"}){
            assertTrue(ScmCommandRouting.isActiveManualControl(type, 0, 1, false));
            assertFalse(ScmCommandRouting.isActiveManualControl(type, 0, 1, true));
        }
        for(String type : new String[]{"ship_forward", "ship_yaw", "ship_pitch", "ship_roll", "ship_ascend"}){
            assertTrue(ScmCommandRouting.isActiveManualControl(type, 1, 1, true), type);
        }
        assertFalse(ScmCommandRouting.isActiveManualControl("ship_stop", 1, 1, false));
    }

    @Test void motionHoldsReplaceConflictingCommandsOnly(){
        for(String type : new String[]{"ship_forward", "ship_navigate", "ship_follow", "ship_climb", "ship_ascend"}){
            assertTrue(ScmCommandRouting.supersededByMotionHold("ship_brake", type), type);
            assertTrue(ScmCommandRouting.supersededByMotionHold(type, "ship_brake"), type);
        }
        for(String type : new String[]{"ship_yaw_left", "ship_yaw_right", "ship_pan"}){
            assertTrue(ScmCommandRouting.supersededByMotionHold("ship_hold_yaw", type));
            assertTrue(ScmCommandRouting.supersededByMotionHold(type, "ship_hold_yaw"));
        }
        for(String type : new String[]{"ship_forward", "ship_pitch", "ship_roll", "ship_hover"}){
            assertFalse(ScmCommandRouting.supersededByMotionHold("ship_hold_yaw", type));
        }
        for(String type : new String[]{"ship_yaw", "ship_pitch", "ship_roll", "ship_hover", "ship_stabilize", "ship_face", "ship_hold_yaw"}){
            assertFalse(ScmCommandRouting.supersededByMotionHold("ship_brake", type), type);
        }
        assertTrue(ScmCommandRouting.isActiveManualControl("ship_hold_yaw", 0, 1));
        assertFalse(ScmCommandRouting.isActiveManualControl("ship_hold_yaw", 1, 0));
    }
}
