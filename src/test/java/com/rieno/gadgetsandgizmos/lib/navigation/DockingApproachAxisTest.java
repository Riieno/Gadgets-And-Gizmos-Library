package com.rieno.gadgetsandgizmos.lib.navigation;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DockingApproachAxisTest{
    // Preserve height on any placement axis without changing finite detection endpoints
    @Test void projectsBeyondDetectionRangeWithoutMovingAlongTheAxis(){
        Vec3 tip = new Vec3(10, 20, 30);
        for(Vec3 up : new Vec3[]{new Vec3(0, 1, 0), new Vec3(1, 0, 0), new Vec3(0, 0, -1), new Vec3(0, -1, 0)}){
            var axis = new DockingApproachAxis(tip, up, 10);
            Vec3 side = up.x == 0 ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1);
            Vec3 reference = tip.add(up.scale(14)).add(side.scale(8));
            assertEquals(tip.add(up.scale(14)), axis.project(reference));
            assertEquals(axis.endpoint(), axis.closestPoint(reference));
        }
    }
}
