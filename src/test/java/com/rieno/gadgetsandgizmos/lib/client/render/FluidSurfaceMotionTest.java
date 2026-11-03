package com.rieno.gadgetsandgizmos.lib.client.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FluidSurfaceMotionTest{
    @Test
    void localGravityFollowsItemRotationAndIgnoresPosition(){
        var transform = new Matrix4f().translate(40, -19, 15).rotateZ((float)Math.PI / 2).scale(.75F);
        var up = FluidSurfaceMotion.localUp(transform, new Vector3f(0, 1, 0));
        assertEquals(1, up.x, 1.0E-6F);
        assertEquals(0, up.y, 1.0E-6F);
        assertEquals(0, up.z, 1.0E-6F);
        var reflectedGui = new Matrix4f().scale(16, -16, 16);
        assertEquals(new Vector3f(0, 1, 0), FluidSurfaceMotion.localUp(reflectedGui, new Vector3f(0, -1, 0)));
    }

    @Test
    void tiltLagsThenSettlesEvenAfterTheBottleIsTurnedUpsideDown(){
        var motion = new FluidSurfaceMotion();
        motion.update(new Vector3f(0, 1, 0), 0);
        var first = motion.update(new Vector3f(1, 0, 0), .25D);
        assertTrue(first.x > 0 && first.x < .5F);
        assertTrue(first.y > .5F);
        assertEquals(first, motion.update(new Vector3f(0, 0, 1), .25D));
        var settled = first;
        for(int idx = 2; idx <= 400; idx++) settled = motion.update(new Vector3f(1, 0, 0), idx * .25D);
        assertEquals(1, settled.x, 1.0E-4F);
        assertEquals(0, settled.y, 1.0E-3F);
        for(int idx = 401; idx <= 800; idx++) settled = motion.update(new Vector3f(-1, 0, 0), idx * .25D);
        assertTrue(settled.isFinite());
        assertEquals(-1, settled.x, 1.0E-4F);
        assertEquals(1, settled.length(), 1.0E-6F);
    }

    @Test
    void frameRatesGiveComparableMotionAndLongGapsResetTheSurface(){
        var fast = new FluidSurfaceMotion();
        var slow = new FluidSurfaceMotion();
        fast.update(new Vector3f(0, 1, 0), 0);
        slow.update(new Vector3f(0, 1, 0), 0);
        Vector3f a = new Vector3f(), b = new Vector3f();
        for(int idx = 1; idx <= 60; idx++) a = fast.update(new Vector3f(1, 0, 0), idx / 6D);
        for(int idx = 1; idx <= 15; idx++) b = slow.update(new Vector3f(1, 0, 0), idx * 2D / 3);
        assertTrue(a.distance(b) < .01F);
        assertEquals(new Vector3f(0, 0, 1), fast.update(new Vector3f(0, 0, 1), 1000));
        assertEquals(new Vector3f(0, 1, 0), fast.update(new Vector3f(Float.NaN, 0, 0), 1));
    }
}
