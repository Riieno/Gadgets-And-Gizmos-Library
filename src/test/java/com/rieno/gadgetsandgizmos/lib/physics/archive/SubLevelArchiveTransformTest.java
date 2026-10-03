package com.rieno.gadgetsandgizmos.lib.physics.archive;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SubLevelArchiveTransformTest{
    @Test void rotatesAroundArchivedAnchor(){
        Quaterniond turn = new Quaterniond().rotateY(Math.PI / 2.0D);
        Vec3 point = SubLevelArchiveTransform.point(new Vec3(12, 5, 10),
                new Vec3(10, 5, 10), new Vec3(20, 8, 30), turn);
        assertEquals(20, point.x, 0.0001);
        assertEquals(8, point.y, 0.0001);
        assertEquals(28, point.z, 0.0001);
    }

    @Test void boundsIncludeAllRotatedCorners(){
        Quaterniond turn = new Quaterniond().rotateY(Math.PI / 2.0D);
        AABB box = SubLevelArchiveTransform.bounds(new AABB(10, 0, 10, 12, 2, 11),
                new Vec3(10, 0, 10), Vec3.ZERO, turn);
        assertEquals(0, box.minX, 0.0001);
        assertEquals(1, box.maxX, 0.0001);
        assertEquals(-2, box.minZ, 0.0001);
        assertEquals(0, box.maxZ, 0.0001);
    }
}
