package com.rieno.gadgetsandgizmos.lib.physics;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NozzleParticleDirectionTest {
    @Test
    void plumeTiltsTowardModelUpInAnyBlockOrientation() {
        double cosine = Math.cos(Math.toRadians(22.5D));
        double sine = Math.sin(Math.toRadians(22.5D));
        Vec3 floorMounted = NozzleParticleDirection.tiltToward(new Vec3(1, 0, 0), new Vec3(0, 1, 0), 22.5D);
        assertEquals(cosine, floorMounted.x, 1.0E-9D);
        assertEquals(sine, floorMounted.y, 1.0E-9D);
        Vec3 wallMounted = NozzleParticleDirection.tiltToward(new Vec3(0, 0, -1), new Vec3(1, 0, 0), 22.5D);
        assertEquals(-cosine, wallMounted.z, 1.0E-9D);
        assertEquals(sine, wallMounted.x, 1.0E-9D);
    }
}
