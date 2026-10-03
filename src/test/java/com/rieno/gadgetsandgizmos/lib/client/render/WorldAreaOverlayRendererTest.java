package com.rieno.gadgetsandgizmos.lib.client.render;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldAreaOverlayRendererTest{
    @Test void selectsNearestWallOfEightCornerBox(){
        Vec3[] corners = {
                new Vec3(0, 0, 0), new Vec3(2, 0, 0), new Vec3(2, 0, 2), new Vec3(0, 0, 2),
                new Vec3(0, 2, 0), new Vec3(2, 2, 0), new Vec3(2, 2, 2), new Vec3(0, 2, 2)
        };
        var hit = WorldAreaOverlayRenderer.hitFace(corners, new Vec3(-2, 1, 1), new Vec3(4, 1, 1));
        assertNotNull(hit);
        assertEquals(Direction.WEST, hit.face());
        assertEquals(2.0D, hit.distance(), 1.0E-6D);
        assertNull(WorldAreaOverlayRenderer.hitFace(corners, new Vec3(-2, 3, 1), new Vec3(4, 3, 1)));
    }
}
