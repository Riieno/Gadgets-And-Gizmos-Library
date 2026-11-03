package com.rieno.gadgetsandgizmos.lib.geometry;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Keep clipped rays correct at plot boundaries and for rays starting inside a plot
class RayBoundsTest{
    private final AABB box = new AABB(10, 10, 10, 11, 11, 11);

    // Bound traversal work by the crossed plot extent instead of the configured ray range
    @Test void longRaysOnlyWalkThePlotInterior(){
        var segment = RayBounds.clip(box, new Vec3(-1024, 10.5, 10.5), new Vec3(1024, 10.5, 10.5));
        assertNotNull(segment);
        assertEquals(10, segment.from().x, 1.0E-9);
        assertEquals(11, segment.to().x, 1.0E-9);
    }

    // Retain inside starts and axis-parallel rays without dividing by zero
    @Test void insideAndParallelRaysRetainTheirEndpoints(){
        var from = new Vec3(10.5, 10.5, 10.5);
        var segment = RayBounds.clip(box, from, new Vec3(10.5, 100, 10.5));
        assertNotNull(segment);
        assertEquals(from, segment.from());
        assertEquals(11, segment.to().y, 1.0E-9);
        assertNull(RayBounds.clip(box, new Vec3(9, 0, 10), new Vec3(9, 100, 10)));
    }
}
