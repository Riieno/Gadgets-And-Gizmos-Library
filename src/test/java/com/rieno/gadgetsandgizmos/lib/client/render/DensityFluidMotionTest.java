package com.rieno.gadgetsandgizmos.lib.client.render;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DensityFluidMotionTest{
    @Test
    void renewedMovementContinuesFromTheVisibleReturningSurface(){
        var motion = new DensityFluidMotion();
        var up = new Vector3f(0, 1, 0);
        var pos = new Vector3f();
        motion.update(up, pos, 0);
        for(int tick = 1; tick <= 8; tick++) motion.update(up, pos.set(tick * .05F, 0, 0), tick);
        DensityFluidMotion.Frame before = null;
        for(int frame = 1; frame <= 33; frame++) before = motion.update(up, pos, 8 + frame / 4D);
        var resumed = motion.update(up, new Vector3f(.41F, 0, 0), 16.25001D);
        assertEquals(0, motion.restProgress());
        assertEquals(before.waveX(), resumed.waveX(), 1.0E-6F);
        assertEquals(before.waveZ(), resumed.waveZ(), 1.0E-6F);
        assertEquals(before.mixing(), resumed.mixing(), 1.0E-6F);
        assertTrue(before.up().distance(resumed.up()) < 1.0E-6F);
    }

    @Test
    void quietContainerEasesToRestOverOneSecondAtFractionalRenderTimes(){
        var motion = new DensityFluidMotion();
        var up = new Vector3f(0, 1, 0);
        var pos = new Vector3f();
        var rotation = new Quaternionf();
        motion.update(up, pos, rotation, 0);
        assertEquals(1, motion.restProgress());
        for(int tick = 1; tick <= 8; tick++){
            pos.set(tick * .04F, 0, 0);
            rotation.rotationY(tick * .1F);
            motion.update(up, pos, rotation, tick);
        }
        assertEquals(0, motion.restProgress());
        for(int frame = 1; frame <= 80; frame++){
            var state = motion.update(up, pos, rotation, 8 + frame / 4D);
            assertEquals(frame / 80F, motion.restProgress(), 1.0E-6F);
            assertTrue(state.up().isFinite());
            if(frame == 40) assertTrue(state.mixing() > 0, "The middle of the return must still show residual motion");
            if(frame == 80){
                assertEquals(0, state.mixing());
                assertEquals(0, state.waveX());
                assertEquals(0, state.waveZ());
                assertTrue(state.up().distance(up) < 1.0E-6F);
            }
        }
        long steps = motion.steps();
        motion.update(up, pos, rotation, 29);
        assertEquals(steps, motion.steps(), "Settled previews must stop simulation work");
        motion.update(up, new Vector3f(.5F, 0, 0), rotation, 30);
        assertEquals(0, motion.restProgress(), "New movement must cancel the resting transition");
    }

    @Test
    void horizontalTurnsStirTheLiquidWithoutTiltingTheSurface(){
        var motion = new DensityFluidMotion();
        var up = new Vector3f(0, 1, 0);
        var pos = new Vector3f();
        motion.update(up, pos, new Quaternionf(), 0);
        var rotation = new Quaternionf().rotateY(.8F);
        motion.update(up, pos, rotation, 1);
        var stirred = motion.update(up, pos, rotation, 2);
        assertTrue(stirred.mixing() > 0);
        assertTrue(Math.abs(stirred.waveX()) + Math.abs(stirred.waveZ()) > 0);
        assertTrue(stirred.up().isFinite());
        var negated = new Quaternionf(-rotation.x, -rotation.y, -rotation.z, -rotation.w);
        var settled = stirred;
        for(int tick = 3; tick <= 400; tick++) settled = motion.update(up, pos, tick % 2 == 0 ? rotation : negated, tick);
        assertEquals(0, settled.mixing());
    }

    @Test
    void movementExcitesWavesAndMixingThenRestoresSeparateSettledLayers(){
        var motion = new DensityFluidMotion();
        var up = new Vector3f(0, 1, 0);
        var tilted = new Vector3f(.5F, .7F, .4F).normalize();
        var position = new Vector3f(.2F, .1F, 0);
        motion.update(up, new Vector3f(), 0);
        var before = motion.update(tilted, position, .5D);
        assertEquals(0, before.mixing());
        motion.update(tilted, position, 1);
        var half = motion.update(tilted, position, 1.5D);
        var full = motion.update(tilted, position, 2);
        assertTrue(full.mixing() > 0);
        assertTrue(half.mixing() > 0 && half.mixing() < full.mixing());
        assertTrue(Math.abs(full.waveX()) + Math.abs(full.waveZ()) > 0);
        assertSame(full, motion.update(up, new Vector3f(), 2));
        var settled = full;
        for(int tick = 3; tick <= 400; tick++) settled = motion.update(tilted, position, tick);
        assertEquals(0, settled.mixing());
        assertEquals(0, settled.waveX());
        assertEquals(0, settled.waveZ());
        assertTrue(settled.up().distance(tilted) < 1.0E-5F);
        long steps = motion.steps();
        for(int frame = 1; frame <= 600; frame++) assertSame(settled, motion.update(tilted, position, 400 + frame / 3D));
        assertEquals(steps, motion.steps());
    }

    @Test
    void renderRateDoesNotMultiplySimulationStepsAndCatchUpIsCapped(){
        var motion = new DensityFluidMotion();
        motion.update(new Vector3f(0, 1, 0), new Vector3f(), 0);
        for(int frame = 1; frame <= 120; frame++){
            motion.update(new Vector3f(.6F, 1, .2F), new Vector3f(frame * .001F, 0, 0), frame / 6D);
        }
        assertEquals(20, motion.steps());
        long steps = motion.steps();
        motion.update(new Vector3f(1, 0, 0), new Vector3f(.3F, 0, 0), 29);
        assertEquals(4, motion.steps() - steps);
        var reset = motion.update(new Vector3f(0, -1, 0), new Vector3f(), 1000);
        assertEquals(0, reset.mixing());
        assertEquals(-1, reset.upY());
    }
}
