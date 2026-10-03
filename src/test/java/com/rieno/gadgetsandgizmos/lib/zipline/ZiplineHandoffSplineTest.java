package com.rieno.gadgetsandgizmos.lib.zipline;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZiplineHandoffSplineTest {
    @Test
    void connectsRopeEndsWithoutPositionOrDirectionJumps() {
        Vec3 start = new Vec3(0, 70, 0);
        Vec3 end = new Vec3(1, 70, 0);
        ZiplineHandoffSpline spline = new ZiplineHandoffSpline(start,
                new Vec3(1, 0, 0), end, new Vec3(1, 0, 0));
        assertEquals(start, spline.sample(0));
        assertEquals(end, spline.sample(1));
        assertTrue(spline.length() >= start.distanceTo(end));
        Vec3 previous = start;
        for (int index = 1; index <= 20; index++) {
            Vec3 point = spline.sample(index / 20.0D);
            assertTrue(point.distanceTo(previous) < 0.15D);
            assertTrue(spline.tangent(index / 20.0D).x > 0.0D);
            previous = point;
        }
    }
}
