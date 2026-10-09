package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScmLinearMotionFeedbackTest{
    @Test
    void stationaryPoseRejectsARepeatedGravityImpulseOffset(){
        var sampler = new ScmLinearMotionFeedback();
        Vec3 solver = new Vec3(0, -0.13, 0);
        assertEquals(solver, sampler.sample(0, Vec3.ZERO, solver, .05));
        assertEquals(Vec3.ZERO, sampler.sample(1, Vec3.ZERO, solver, .05));
        assertEquals(Vec3.ZERO, sampler.sample(1, new Vec3(100, 0, 0), solver, .05));
    }

    @Test
    void accelerationIsExtrapolatedRatherThanDelayedByOneControlTick(){
        var sampler = new ScmLinearMotionFeedback();
        sampler.sample(0, Vec3.ZERO, new Vec3(0, -0.13, 0), .05);
        assertEquals(1D, sampler.sample(1, new Vec3(.025, 0, 0), new Vec3(1, -.13, 0), .05).x, 1.0E-8);
        assertEquals(2D, sampler.sample(2, new Vec3(.1, 0, 0), new Vec3(2, -.13, 0), .05).x, 1.0E-8);
    }

    @Test
    void teleportsMissingTicksAndResetsUseTheSolverVelocity(){
        var sampler = new ScmLinearMotionFeedback();
        sampler.sample(0, Vec3.ZERO, Vec3.ZERO, .05);
        Vec3 moving = new Vec3(2, 0, 0);
        assertEquals(moving, sampler.sample(1, new Vec3(100, 0, 0), moving, .05));
        assertEquals(moving, sampler.sample(4, new Vec3(100, 0, 0), moving, .05));
        sampler.reset();
        assertEquals(moving, sampler.sample(5, new Vec3(100, 0, 0), moving, .05));
    }
}
