package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ScmDockingOrientationTest{
    @Test
    void lateralDockChoosesLateralConnectorEvenWhenItFacesAway(){
        Vec3 shipUp = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 targetFacing = new Vec3(1.0D, 0.0D, 0.0D);
        double lateral = ScmDockingOrientation.score(
                new Vec3(-1.0D, 0.0D, 0.0D), shipUp,
                shipUp, targetFacing, shipUp);
        double downward = ScmDockingOrientation.score(
                new Vec3(0.0D, -1.0D, 0.0D),
                new Vec3(0.0D, 0.0D, 1.0D),
                shipUp, targetFacing, shipUp);
        assertTrue(lateral > downward);
    }

    @Test
    void verticalDockChoosesDownwardConnector(){
        Vec3 shipUp = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 targetFacing = new Vec3(0.0D, -1.0D, 0.0D);
        double lateral = ScmDockingOrientation.score(
                new Vec3(1.0D, 0.0D, 0.0D), shipUp,
                shipUp, targetFacing, new Vec3(0.0D, 0.0D, 1.0D));
        double downward = ScmDockingOrientation.score(
                targetFacing, new Vec3(0.0D, 0.0D, 1.0D),
                shipUp, targetFacing, new Vec3(0.0D, 0.0D, 1.0D));
        assertTrue(downward > lateral);
    }

    @Test
    void oppositeFacingStillHasATurnAxis(){
        Vec3 error = ScmDockingOrientation.facingError(
                new Vec3(-1.0D, 0.0D, 0.0D),
                new Vec3(0.0D, 1.0D, 0.0D),
                new Vec3(1.0D, 0.0D, 0.0D));
        assertTrue(error.y > 0.9D);
    }
}
