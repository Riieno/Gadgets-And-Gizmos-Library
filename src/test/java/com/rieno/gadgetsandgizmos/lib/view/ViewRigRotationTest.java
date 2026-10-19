package com.rieno.gadgetsandgizmos.lib.view;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ViewRigRotationTest{
    @Test void manualPanCrossesBothEndpointsAndContinuesForManyRevolutions(){
        var rig = new ViewRig();
        for(int direction : new int[]{1, -1}){
            rig.aim(direction * 179, -90);
            for(int step = 1; step <= 4000; step++){
                rig.turn(direction * 7, 0);
                double expected = direction * (179 + step * 7);
                var actual = new ViewPose(Vec3.ZERO, ViewRig.orientation(ViewRig.angles(ViewRig.Mode.MANUAL, 0, rig.pan(), rig.tilt())), 70).forward();
                var bearing = new ViewPose(Vec3.ZERO, ViewRig.orientation(new ViewRig.Angles(expected, -90)), 70).forward();
                assertTrue(actual.distanceTo(bearing) < 1e-10);
                assertTrue(Math.abs(rig.pan()) <= 180);
            }
        }
    }
    @Test void absoluteAnglesWrapAndTiltKeepsItsHingeLimits(){
        var rig = new ViewRig();
        rig.aim(735, 900);
        assertEquals(15, rig.pan()); assertEquals(135, rig.tilt());
        rig.aim(-735, -900);
        assertEquals(-15, rig.pan()); assertEquals(-135, rig.tilt());
        rig.aim(Double.POSITIVE_INFINITY, Double.NaN);
        assertEquals(0, rig.pan()); assertEquals(0, rig.tilt());
    }
}
