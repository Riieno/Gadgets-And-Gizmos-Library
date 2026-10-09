package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// Combine active intent while automatic feedback yields only its claimed axes
class ScmOptionalControlPriorityTest{
    @Test
    void activeOptionalIntentAddsAndSubtractsOnSharedAxesInEveryCraftFrame(){
        for(Direction forward : Direction.values()) for(Direction up : Direction.values()){
            if(forward.getAxis() == up.getAxis()) continue;
            var frame = new ScmOrientation(forward, up);
            Vec3 primary = frame.upVector().scale(7);
            for(double amount : new double[]{3, -3, -7}){
                Vec3 torque = frame.upVector().scale(amount);
                var res = ScmControlPriority.additionalWrench(
                        new ScmWrenchSourceRegistry.Wrench(true, Vec3.ZERO, torque), Vec3.ZERO);
                assertEquals(7 + amount, primary.add(res.torque()).dot(frame.upVector()), 1.0E-9);
            }
        }
        var idle = new ScmWrenchSourceRegistry.Wrench(false, new Vec3(100, 100, 100), new Vec3(100, 100, 100));
        assertEquals(ScmWrenchSourceRegistry.Wrench.NONE, ScmControlPriority.additionalWrench(idle, Vec3.ZERO));
    }

    @Test
    void activeLiftFeedbackCombinesWithoutDuplicatingGravity(){
        var request = new ScmWrenchSourceRegistry.Wrench(true, new Vec3(5, 1020, 8),
                new Vec3(1, -2, 3), new Vec3(0, 1000, 3));
        var res = ScmControlPriority.additionalWrench(request, new Vec3(0, 1000, 0));
        assertEquals(new Vec3(5, 20, 8), res.force());
        assertEquals(request.torque(), res.torque());
        assertEquals(new Vec3(0, 0, 3), res.compensationForce());
    }

    @Test
    void mouseSteeringRetainsTranslationWhileAutomaticHeadingYields(){
        for(Direction forward : Direction.values()) for(Direction up : Direction.values()){
            if(forward.getAxis() == up.getAxis()) continue;
            var frame = new ScmOrientation(forward, up);
            Vec3 movement = frame.forwardVector().scale(40).add(frame.upVector().scale(100));
            Vec3 steering = frame.upVector().scale(-9);
            var extra = ScmControlPriority.additionalWrench(new ScmWrenchSourceRegistry.Wrench(
                    true, Vec3.ZERO, steering, Vec3.ZERO, Set.of("ship_yaw")), frame.upVector().scale(100));
            Vec3 heading = ScmControlPriority.remainingTorque(frame.upVector().scale(30)
                    .add(frame.rightVector().scale(4)), extra.rotationActions(), frame.forwardVector(), frame.upVector());
            assertEquals(movement, movement.add(extra.force()));
            assertEquals(-9, heading.add(extra.torque()).dot(frame.upVector()), 1.0E-9);
            assertEquals(4, heading.add(extra.torque()).dot(frame.rightVector()), 1.0E-9);
            assertEquals(-6, frame.upVector().scale(3).add(extra.torque()).dot(frame.upVector()), 1.0E-9);
        }
    }

    @Test
    void directYawLeavesOptionalPitchAndRollAvailable(){
        for(Direction forward : Direction.values()) for(Direction up : Direction.values()){
            if(forward.getAxis() == up.getAxis()) continue;
            var frame = new ScmOrientation(forward, up);
            Vec3 request = frame.rightVector().scale(3).add(frame.upVector().scale(4)).add(frame.forwardVector().scale(5));
            Vec3 res = ScmControlPriority.remainingTorque(request, Set.of("ship_yaw_left"), frame.forwardVector(), frame.upVector());
            assertEquals(0, res.dot(frame.upVector()), 1.0E-9);
            assertEquals(3, res.dot(frame.rightVector()), 1.0E-9);
            assertEquals(5, res.dot(frame.forwardVector()), 1.0E-9);
        }
    }

    @Test
    void navigationOwnsEveryAxisWhileIdleCommandsLeaveOptionalRequestsIntact(){
        Vec3 request = new Vec3(2, 3, 4);
        Vec3 forward = new Vec3(0, 0, -1);
        Vec3 up = new Vec3(0, 1, 0);
        assertEquals(request, ScmControlPriority.remainingForce(request, Set.of(), forward, up));
        assertEquals(request, ScmControlPriority.remainingTorque(request, Set.of(), forward, up));
        assertEquals(Vec3.ZERO, ScmControlPriority.remainingForce(request,
                Set.of("ship_forward", "ship_strafe_left", "ship_ascend"), forward, up));
        assertEquals(Vec3.ZERO, ScmControlPriority.remainingTorque(request,
                Set.of("ship_yaw", "ship_pitch", "ship_roll"), forward, up));
    }

    @Test
    void primaryLiftDoesNotRemoveOptionalVelocityFeedbackOrPerpendicularDrag(){
        Vec3 request = new Vec3(5, 1020, 8);
        Vec3 compensation = new Vec3(0, 1000, 3);
        assertEquals(new Vec3(5, 20, 8), ScmControlPriority.withoutSharedCompensation(
                request, compensation, new Vec3(0, 1000, 0)));
        assertEquals(request, ScmControlPriority.withoutSharedCompensation(request, compensation, Vec3.ZERO));
        Vec3 rotated = new Vec3(1020, 5, 8);
        assertEquals(new Vec3(20, 5, 8), ScmControlPriority.withoutSharedCompensation(
                rotated, new Vec3(1000, 0, 3), new Vec3(1000, 0, 0)));
    }
}
