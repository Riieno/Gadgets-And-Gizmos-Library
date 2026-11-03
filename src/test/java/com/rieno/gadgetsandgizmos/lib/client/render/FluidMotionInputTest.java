package com.rieno.gadgetsandgizmos.lib.client.render;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FluidMotionInputTest{
    @Test
    void shakenBottleRecoversItsDensityLayersWithinFourSeconds(){
        var palette = new com.rieno.gadgetsandgizmos.lib.color.DensityColorPalette(java.util.List.of(
                new com.rieno.gadgetsandgizmos.lib.color.DensityColorPalette.Layer(0xFF0000, 1, 0),
                new com.rieno.gadgetsandgizmos.lib.color.DensityColorPalette.Layer(0x0000FF, 1, 15)));
        var field = new DensityFluidField(new net.minecraft.world.phys.AABB(0, 0, 0, 1, 1, 1), .85F, palette);
        var motion = new DensityFluidMotion();
        var pose = new Matrix4f().translation(.4F, -.3F, -.8F);
        DensityFluidMotion.Frame frame = null;
        for(int tick = 0; tick <= 60; tick++){
            double x = Math.sin(tick * .2D) * .4D;
            frame = motion.update(FluidMotionInput.capture(pose, new Quaternionf().rotateY((float)Math.sin(tick * .16D) * .6F),
                    new Vec3(x, 80, 0), false, tick));
            field.update(frame, tick);
        }
        assertTrue(frame.mixing() > .1F);
        for(int tick = 61; tick <= 140; tick++){
            frame = motion.update(FluidMotionInput.capture(pose, new Quaternionf(), new Vec3(0, 80, 0), false, tick));
            field.update(frame, tick);
        }
        assertEquals(0, frame.mixing());
        assertEquals(0, frame.waveX());
        assertEquals(0, frame.waveZ());
        assertTrue(field.settled());
    }

    @Test
    void slowHorizontalCameraMotionDoesNotDisappearThroughFloatRounding(){
        var motion = new DensityFluidMotion();
        var pose = new Matrix4f().translation(.4F, -.3F, -.8F);
        motion.update(FluidMotionInput.capture(pose, new Quaternionf(), Vec3.ZERO, false, 0));
        float mixing = 0;
        for(int frame = 1; frame <= 600; frame++){
            var input = FluidMotionInput.capture(pose, new Quaternionf().rotateY(frame * .00003F), Vec3.ZERO, false, frame / 12D);
            mixing = Math.max(mixing, motion.update(input).mixing());
        }
        assertTrue(motion.steps() > 40);
        assertTrue(mixing > 0);
    }

    @Test
    void strafingWithAnUnchangedHeldItemPoseCreatesVisibleInertiaAtWorldCoordinates(){
        var motion = new DensityFluidMotion();
        var pose = new Matrix4f().translation(.4F, -.3F, -.8F);
        motion.update(FluidMotionInput.capture(pose, new Quaternionf(), new Vec3(29000000, 80, 29000000), false, 0));
        DensityFluidMotion.Frame frame = null;
        float maximumTilt = 0, maximumWave = 0, maximumMix = 0;
        for(int tick = 1; tick <= 12; tick++){
            frame = motion.update(FluidMotionInput.capture(pose, new Quaternionf(), new Vec3(29000000 + tick * .12D, 80, 29000000), false, tick));
            maximumTilt = Math.max(maximumTilt, Math.abs(frame.upX()));
            maximumWave = Math.max(maximumWave, Math.abs(frame.waveX()));
            maximumMix = Math.max(maximumMix, frame.mixing());
        }
        assertTrue(maximumTilt > .1F, "Horizontal acceleration must tilt the rendered surface");
        assertTrue(maximumWave > .05F, "Camera translation must excite visible sloshing");
        assertTrue(maximumMix > .1F);
        float waveAtStop = frame.waveX();
        for(int tick = 13; tick <= 18; tick++) frame = motion.update(FluidMotionInput.capture(pose, new Quaternionf(),
                new Vec3(29000001.44D, 80, 29000000), false, tick));
        assertTrue(Math.abs(frame.waveX() - waveAtStop) > .02F, "Stopping must create a counter impulse");
        assertTrue(Math.abs(frame.waveX()) > .01F, "Liquid must continue moving after the player stops");
    }

    @Test
    void ordinaryHorizontalCameraTurnsProduceInertiaWithoutPitchChanges(){
        var motion = new DensityFluidMotion();
        var pose = new Matrix4f().translation(.4F, -.3F, -.8F).rotateX(.1F);
        motion.update(FluidMotionInput.capture(pose, new Quaternionf(), Vec3.ZERO, false, 0));
        float wave = 0, mixing = 0, tilt = 0;
        for(int tick = 1; tick <= 30; tick++){
            var camera = new Quaternionf().rotateY((float)Math.sin(tick * .18D) * .45F);
            var frame = motion.update(FluidMotionInput.capture(pose, camera, Vec3.ZERO, false, tick));
            wave = Math.max(wave, Math.abs(frame.waveX()) + Math.abs(frame.waveZ()));
            mixing = Math.max(mixing, frame.mixing());
            tilt = Math.max(tilt, Math.abs(frame.upX()));
        }
        assertTrue(wave > .1F);
        assertTrue(mixing > .3F);
        assertTrue(tilt > .02F);
    }

    @Test
    void movingTheCameraDoesNotShakeAnInventoryIcon(){
        var pose = new Matrix4f().translation(120, 90, 150).scale(16, -16, 16);
        var first = FluidMotionInput.capture(pose, new Quaternionf(), Vec3.ZERO, true, 0);
        var second = FluidMotionInput.capture(pose, new Quaternionf().rotateY(1), new Vec3(40, 80, 90), true, 1);
        assertEquals(first.x(), second.x());
        assertEquals(first.y(), second.y());
        assertEquals(first.z(), second.z());
        assertEquals(first.rotation(), second.rotation());
        assertEquals(first.up(), second.up());
    }
}
