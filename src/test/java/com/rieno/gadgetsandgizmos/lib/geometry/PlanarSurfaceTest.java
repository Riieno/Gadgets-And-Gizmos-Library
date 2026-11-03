package com.rieno.gadgetsandgizmos.lib.geometry;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlanarSurfaceTest{
    private static final PlanarSurface SCREEN = new PlanarSurface(
            new Vec3(0, 0, 0), new Vec3(0, 0, 1), new Vec3(2, 0, 1), new Vec3(2, 0, 0));

    // Pointer coordinates must follow both the model tilt and its mounting rotation
    @Test void tiltedScreensRetainPointerCoordinates(){
        for(double pitch : new double[]{0, -22.5D, -45, -67.5D}){
            for(double yaw : new double[]{0, 90, 180, 270}){
                Quaterniond rotation = new Quaterniond().rotateY(Math.toRadians(yaw)).rotateX(Math.toRadians(pitch));
                PlanarSurface screen = SCREEN.rotate(rotation, new Vec3(0.5D, 0.5D, 0.5D));
                Vec3 point = screen.topLeft().add(screen.topRight().subtract(screen.topLeft()).scale(0.25D))
                        .add(screen.bottomLeft().subtract(screen.topLeft()).scale(0.75D));
                Vec3 normal = screen.normal();
                PlanarSurface.Point hit = screen.intersect(point.add(normal.scale(3)), point.subtract(normal));
                assertNotNull(hit);
                assertEquals(0.25D, hit.x(), 1.0E-10D);
                assertEquals(0.75D, hit.y(), 1.0E-10D);
                assertEquals(2.0D, screen.topRight().distanceTo(screen.topLeft()), 1.0E-10D);
                assertEquals(1.0D, screen.bottomLeft().distanceTo(screen.topLeft()), 1.0E-10D);
            }
        }
    }

    @Test void backFacesParallelRaysAndShortReachCannotClick(){
        assertNull(SCREEN.intersect(new Vec3(1, -1, 0.5D), new Vec3(1, 1, 0.5D)));
        assertNull(SCREEN.intersect(new Vec3(0, 1, 0), new Vec3(2, 1, 1)));
        assertNull(SCREEN.intersect(new Vec3(1, 2, 0.5D), new Vec3(1, 1, 0.5D)));
        assertNull(SCREEN.intersect(new Vec3(3, 1, 0.5D), new Vec3(3, -1, 0.5D)));
    }

    // A tilted panel's enclosing box must not move the pointer along the screen
    @Test void obliqueRayUsesTheScreenPlane(){
        PlanarSurface screen = SCREEN.rotate(new Quaterniond().rotateX(-Math.PI / 4), Vec3.ZERO);
        PlanarSurface.Point hit = screen.intersect(new Vec3(1, 2, 0.3D), new Vec3(1, -2, 0.3D));
        assertNotNull(hit);
        assertEquals(0.5D, hit.x(), 1.0E-10D);
        assertEquals(0.3D * Math.sqrt(2), hit.y(), 1.0E-10D);
    }

    @Test void borderToleranceClampsCoordinatesAndRejectsDegenerateScreens(){
        assertEquals(new PlanarSurface.Point(0, 0), SCREEN.project(new Vec3(-0.01D, 0, -0.01D)));
        assertNull(SCREEN.project(new Vec3(-1, 0, 0)));
        assertNull(new PlanarSurface(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO).project(Vec3.ZERO));
    }
}
