package com.rieno.gadgetsandgizmos.lib.physics;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

// Keep projected block lights outside the surface for different beam directions
class ProjectedLightSourceTest{
    // A half-block offset follows the camera rather than a fixed world axis
    @Test
    void offsetsTowardTheLens(){
        assertEquals(new Vec3(3, 4.5, 5), ProjectedLightSource.sourcePosition(new Vec3(3, 10, 5), new Vec3(3, 4, 5)));
        assertEquals(new Vec3(3.5, 4, 5), ProjectedLightSource.sourcePosition(new Vec3(10, 4, 5), new Vec3(3, 4, 5)));
        assertEquals(new Vec3(3, 4, 4.5), ProjectedLightSource.sourcePosition(new Vec3(3, 4, 0), new Vec3(3, 4, 5)));
    }
    // Close surfaces never place the source behind the camera
    @Test
    void clampsTheOffsetForShortRays(){
        Vec3 lens = new Vec3(0, 0.1, 0);
        assertEquals(lens, ProjectedLightSource.sourcePosition(lens, Vec3.ZERO));
        assertEquals(Vec3.ZERO, ProjectedLightSource.sourcePosition(Vec3.ZERO, Vec3.ZERO));
    }
}
