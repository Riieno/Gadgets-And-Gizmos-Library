package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ScmRecoverySpeedTest{
    private static final double DT = 0.05D;

    @Test void fullStrengthStopsRatesInOneIntervalAndWeakerStrengthRecoversSlower(){
        Vec3 velocity = new Vec3(12, -3, 4);
        Vec3 up = new Vec3(1, 1, 0).normalize();
        Vec3 otherRotation = new Vec3(1, -1, 3);
        for(double strength : new double[]{0, 0.00001, 0.1, 0.5, 1}){
            Vec3 next = velocity.add(ScmMotionDamping.stopLinear(velocity, strength, DT).scale(DT));
            assertEquals(0, next.distanceTo(velocity.scale(1 - strength)), 1e-9);
            Vec3 angular = otherRotation.add(up.scale(2));
            Vec3 corrected = angular.add(ScmMotionDamping.holdYaw(angular, up, strength, DT).scale(DT));
            assertEquals(2 * (1 - strength), corrected.dot(up), 1e-9);
            assertEquals(0, corrected.subtract(up.scale(corrected.dot(up))).distanceTo(otherRotation), 1e-9);
        }
    }

    @Test void strengthSelectsPositionRecoveryTimeAndRespectsLimitedPropulsion(){
        int slow = settle(1, 0.1, 10_000);
        int medium = settle(1, 0.5, 10_000);
        int fast = settle(1, 1, 10_000);
        assertTrue(slow > medium && medium > fast);
        assertTrue(fast <= 5, "Full-strength response took " + fast + " control intervals");
        assertTrue(settle(10, 1, 2) > fast, "Recovery ignored propulsion capacity");
        assertTrue(settle(10, 1, 2) < settle(10, 0.1, 2));
    }

    @Test void zeroAndInvalidStrengthProduceNoRecoveryAndTinyPositiveStrengthStillEngages(){
        for(double strength : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}){
            assertEquals(0, ScmStateFeedback.recoveryAcceleration(3, 4, strength, DT));
            assertEquals(0, ScmStateFeedback.recoveryAcceleration(3, 4, strength, DT, 20, 5));
            assertEquals(Vec3.ZERO, ScmMotionDamping.stopLinear(new Vec3(1, 2, 3), strength, DT));
        }
        assertTrue(ScmStateFeedback.recoveryAcceleration(3, 0, 0.00001, DT) > 0);
        assertThrows(IllegalArgumentException.class, () -> ScmStateFeedback.recoveryAcceleration(1, 1, 1, 0));
        assertEquals(0, ScmStateFeedback.recoveryAcceleration(Double.NaN, Double.POSITIVE_INFINITY, 1, DT));
    }

    @Test void adaptiveAttitudeRecoveryUsesStrengthWithoutAddingYaw(){
        var model = ScmAdaptiveStateModel.sample(List.of(
                new ScmAdaptiveStateModel.Actuator(Vec3.ZERO, new Vec3(1000, 0, 0), 0),
                new ScmAdaptiveStateModel.Actuator(Vec3.ZERO, new Vec3(-1000, 0, 0), 0),
                new ScmAdaptiveStateModel.Actuator(new Vec3(0, 1000, 0), Vec3.ZERO, 0),
                new ScmAdaptiveStateModel.Actuator(new Vec3(0, -1000, 0), Vec3.ZERO, 0)), DT, 0.01, 0.001);
        Vec3 error = new Vec3(0.01, 0, 0);
        Vec3 slow = model.angularFeedback(error, Vec3.ZERO, 0.1);
        Vec3 fast = model.angularFeedback(error, Vec3.ZERO, 1);
        assertTrue(fast.x > slow.x && slow.x > 0);
        assertEquals(0, fast.y); assertEquals(0, fast.z);
        assertEquals(Vec3.ZERO, model.angularFeedback(error, new Vec3(1, 2, 3), 0));
        assertTrue(model.feedback(new Vec3(0, 1, 0), false, 0.01, 0, 1).acceleration()
                > model.feedback(new Vec3(0, 1, 0), false, 0.01, 0, 0.1).acceleration());
        assertTrue(Math.abs(model.feedback(new Vec3(0, 1, 0), false, 100, -100, 1).acceleration()) <= 1000.01);
    }

    @Test void recoveryClassificationLeavesMovementAndNavigationTuningAlone(){
        for(String type : new String[]{"ship_hover", "ship_stabilize", "ship_decelerate", "ship_hold_yaw", "ship_brake"}){
            assertTrue(ScmCommandRouting.usesRecoveryStrength(type));
        }
        for(String type : new String[]{"ship_yaw", "ship_pan", "ship_forward", "ship_backward", "ship_accelerate", "ship_ascend", "ship_descend", "ship_navigate", "ship_climb"}){
            assertFalse(ScmCommandRouting.usesRecoveryStrength(type));
        }
    }

    private static int settle(double error, double strength, double authority){
        double rate = 0;
        for(int tick = 1; tick <= 10_000; tick++){
            double acceleration = ScmStateFeedback.recoveryAcceleration(error, rate, strength, DT, authority, authority);
            assertTrue(Double.isFinite(acceleration));
            assertTrue(Math.abs(acceleration) <= authority + 1e-9);
            error -= rate * DT + 0.5D * acceleration * DT * DT;
            rate += acceleration * DT;
            if(Math.abs(error) < 0.001 && Math.abs(rate) < 0.001) return tick;
        }
        fail("Recovery did not settle"); return 0;
    }
}
