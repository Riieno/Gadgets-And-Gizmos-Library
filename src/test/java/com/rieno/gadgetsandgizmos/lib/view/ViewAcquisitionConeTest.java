package com.rieno.gadgetsandgizmos.lib.view;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ViewAcquisitionConeTest{
    private static final Vec3 FORWARD = new Vec3(0, 0, -1);

    @Test void picksPlayersBesideTheCentreRayWithoutAimingAtTheirHitbox(){
        var cone = new ViewAcquisitionCone(Vec3.ZERO, FORWARD, 64, 1.5, 15);
        AABB player = new AABB(2.7, -.9, -10.3, 3.3, .9, -9.7);
        assertTrue(player.clip(Vec3.ZERO, FORWARD.scale(64)).isEmpty());
        Vec3 point = cone.point(player);
        assertNotNull(point);
        assertTrue(cone.contains(point));
        assertTrue(point.x >= player.minX && point.x <= player.maxX);
    }

    @Test void widensWithDistanceButDoesNotAcquireOutsideTheVolume(){
        var cone = new ViewAcquisitionCone(Vec3.ZERO, FORWARD, 64, 1.5, 15);
        assertTrue(cone.contains(new Vec3(10, 0, -40)));
        assertFalse(cone.contains(new Vec3(10, 0, -10)));
        assertFalse(cone.contains(new Vec3(20, 0, -40)));
        assertTrue(cone.contains(new Vec3(1, 0, -1)));
    }

    @Test void rejectsRearAndOutOfRangeBounds(){
        var cone = new ViewAcquisitionCone(Vec3.ZERO, FORWARD, 16, 1.5, 15);
        assertNull(cone.point(new AABB(-1, -1, 1, 1, 1, 3)));
        assertNull(cone.point(new AABB(-1, -1, -20, 1, 1, -17)));
        assertFalse(cone.contains(new Vec3(3, 0, -16)));
        assertNull(new ViewAcquisitionCone(Vec3.ZERO, FORWARD, 0, 1.5, 15).point(new AABB(-1, -1, -1, 1, 1, 1)));
    }

    @Test void detectsBodyEdgesWhenTheirCentreIsOutsideTheCone(){
        var cone = new ViewAcquisitionCone(Vec3.ZERO, FORWARD, 64, 1.5, 15);
        AABB body = new AABB(3, -1, -12, 15, 1, -8);
        assertFalse(cone.contains(body.getCenter()));
        Vec3 point = cone.point(body);
        assertNotNull(point);
        assertTrue(cone.contains(point));
    }

    @Test void findsTheNearPartOfBodiesExtendingPastTheRange(){
        var cone = new ViewAcquisitionCone(Vec3.ZERO, FORWARD, 64, 1.5, 15);
        Vec3 point = cone.point(new AABB(3, -1, -1000, 5, 1, -20));
        assertNotNull(point);
        assertTrue(point.distanceTo(Vec3.ZERO) <= 64);
        assertTrue(cone.contains(point));
    }

    @Test void worksInArbitraryMountedDirectionsAndSupportsACylinder(){
        Vec3 origin = new Vec3(40, 12, -20);
        var cone = new ViewAcquisitionCone(origin, new Vec3(1, 0, 0), 64, 1.5, 15);
        assertTrue(cone.contains(origin.add(10, 3, 0)));
        assertFalse(cone.contains(origin.add(-1, 0, 0)));
        var cylinder = new ViewAcquisitionCone(origin, new Vec3(2, 0, 0), 64, 2, 0);
        assertTrue(cylinder.contains(origin.add(20, 1.9, 0)));
        assertFalse(cylinder.contains(origin.add(20, 2.1, 0)));
    }

    @Test void rejectsInvalidGeometry(){
        assertThrows(IllegalArgumentException.class, () -> new ViewAcquisitionCone(Vec3.ZERO, Vec3.ZERO, 64, 1.5, 15));
        assertThrows(IllegalArgumentException.class, () -> new ViewAcquisitionCone(Vec3.ZERO, FORWARD, Double.NaN, 1.5, 15));
        assertThrows(IllegalArgumentException.class, () -> new ViewAcquisitionCone(Vec3.ZERO, FORWARD, 64, -1, 15));
        assertThrows(IllegalArgumentException.class, () -> new ViewAcquisitionCone(Vec3.ZERO, FORWARD, 64, 1.5, 90));
    }
}
