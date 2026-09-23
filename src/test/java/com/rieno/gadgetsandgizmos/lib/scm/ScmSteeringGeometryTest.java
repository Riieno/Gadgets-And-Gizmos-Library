package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScmSteeringGeometryTest{
    private static final Vec3 FORWARD = new Vec3(1.0D, 0.0D, 0.0D);
    private static final List<Vec3> AXLES = List.of(new Vec3(3.0D, 0.0D, -2.0D),
            new Vec3(3.0D, 0.0D, 2.0D), new Vec3(-2.0D, 0.0D, -2.0D),
            new Vec3(-2.0D, 0.0D, 2.0D));

    @Test
    void previewsAtTheSelectedAxleWithoutIntroducingSidewaysOffset(){
        assertEquals(new Vec3(3.0D, 0.0D, 0.0D), reference(ScmSteeringMode.FRONT_WHEEL, false));
        assertEquals(new Vec3(-2.0D, 0.0D, 0.0D), reference(ScmSteeringMode.REAR_WHEEL, false));
        assertEquals(new Vec3(3.0D, 0.0D, 0.0D), reference(ScmSteeringMode.FOUR_WHEEL, false));
        assertEquals(new Vec3(-2.0D, 0.0D, 0.0D), reference(ScmSteeringMode.FOUR_WHEEL, true));
    }

    @Test
    void sharpTargetsGetMoreSteeringThanSmallCorrections(){
        assertEquals(0.1D, ScmControlAxes.groundSteeringDemand(Math.toRadians(3.0D), 0.0D), 1.0E-8D);
        assertEquals(1.0D, ScmControlAxes.groundSteeringDemand(Math.toRadians(60.0D), 0.0D), 1.0E-8D);
        assertEquals(-1.0D, ScmControlAxes.groundSteeringDemand(Math.toRadians(-60.0D), 0.0D), 1.0E-8D);
        assertEquals(0.15D, ScmControlAxes.groundSteeringDemand(
                Math.toRadians(15.0D), 1.0D, 0.0D), 1.0E-8D);
    }

    private static Vec3 reference(ScmSteeringMode mode, boolean reverse){
        return ScmSteeringGeometry.referencePosition(Vec3.ZERO, FORWARD, AXLES, mode, reverse);
    }
}
