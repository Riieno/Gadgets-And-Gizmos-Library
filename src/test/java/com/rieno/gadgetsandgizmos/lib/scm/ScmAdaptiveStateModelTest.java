package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScmAdaptiveStateModelTest{
    private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);

    @Test
    void stateAndInputMatricesFollowTheLiveActuators(){
        ScmAdaptiveStateModel model = model(List.of(
                new ScmAdaptiveStateModel.Actuator(
                        new Vec3(2.0D, 0.0D, 0.0D),
                        new Vec3(0.0D, 0.0D, 3.0D), 0.25D)));
        assertEquals(1.0D, model.stateMatrix()[0][6]);
        assertEquals(1.0D, model.stateMatrix()[5][11]);
        assertEquals(2.0D, model.inputMatrix()[6][0]);
        assertEquals(3.0D, model.inputMatrix()[11][0]);
        double[][] copy = model.inputMatrix();
        copy[6][0] = 100.0D;
        assertEquals(2.0D, model.inputMatrix()[6][0]);
    }

    @Test
    void massAndAvailableThrustChangeThePlacedPoles(){
        ScmAdaptiveStateModel light = model(List.of(
                new ScmAdaptiveStateModel.Actuator(UP.scale(2.0D), Vec3.ZERO, 0.5D)));
        ScmAdaptiveStateModel heavy = model(List.of(
                new ScmAdaptiveStateModel.Actuator(UP.scale(0.5D), Vec3.ZERO, 0.5D)));
        ScmAdaptiveStateModel.Response quick = light.feedback(UP, false, 1.0D, 0.0D);
        ScmAdaptiveStateModel.Response slow = heavy.feedback(UP, false, 1.0D, 0.0D);
        assertTrue(quick.firstPole() < slow.firstPole());
        assertTrue(quick.acceleration() > slow.acceleration());
    }

    @Test
    void hoverThrottleLeavesAuthorityToAscendAndDescend(){
        ScmAdaptiveStateModel model = model(List.of(
                new ScmAdaptiveStateModel.Actuator(UP.scale(2.0D), Vec3.ZERO, 0.5D)));
        assertTrue(model.authority(UP, false, true) > 0.8D);
        assertTrue(model.authority(UP, false, false) > 0.8D);
        assertTrue(model.feedback(UP, false, -1.0D, 0.0D).acceleration() < 0.0D);
        assertTrue(model.feedback(UP, false, 0.0D, -1.0D).acceleration() > 0.0D);
    }

    @Test
    void coupledTorqueNeedsAnotherActuatorBeforePureLiftIsAvailable(){
        ScmAdaptiveStateModel.Actuator offCenter =
                new ScmAdaptiveStateModel.Actuator(UP,
                        new Vec3(0.0D, 0.0D, 1.0D), 0.0D);
        ScmAdaptiveStateModel alone = model(List.of(offCenter));
        assertEquals(0.0D, alone.authority(UP, false, true), 1.0E-8D);
        ScmAdaptiveStateModel balanced = model(List.of(offCenter,
                new ScmAdaptiveStateModel.Actuator(Vec3.ZERO,
                        new Vec3(0.0D, 0.0D, -1.0D), 0.0D)));
        assertTrue(balanced.authority(UP, false, true) > 0.8D);
    }

    @Test
    void smallTrimmedThrustersKeepDescentAuthorityBesideALargeEngine(){
        ScmAdaptiveStateModel model = model(List.of(
                new ScmAdaptiveStateModel.Actuator(UP.scale(1.5D),
                        new Vec3(0.0D, 0.0D, 1.5D), 0.33D),
                new ScmAdaptiveStateModel.Actuator(UP.scale(1.5D),
                        new Vec3(0.0D, 0.0D, -1.5D), 0.33D),
                new ScmAdaptiveStateModel.Actuator(UP.scale(25.0D),
                        Vec3.ZERO, 0.0D)));
        assertTrue(model.authority(UP, false, false) > 0.8D);
        assertTrue(model.authority(UP, false, true) > 20.0D);
    }

    @Test
    void diagonalPropulsionCanFollowItsOwnDirection(){
        ScmAdaptiveStateModel model = model(List.of(
                new ScmAdaptiveStateModel.Actuator(new Vec3(1.0D, 1.0D, 0.0D),
                        Vec3.ZERO, 0.5D)));
        Vec3 correction = model.linearVelocityFeedback(
                new Vec3(1.0D, 1.0D, 0.0D), Vec3.ZERO, 0.35D);
        assertTrue(correction.x > 0.0D);
        assertEquals(correction.x, correction.y, 1.0E-8D);
    }

    @Test
    void hoverRecoversWithContinuousPrecisionThrust(){
        List<ScmPrecisionAllocator.Unit> units = List.of(
                new ScmPrecisionAllocator.Unit(UP.scale(6.0D),
                        new Vec3(0.0D, 0.0D, 6.0D), true),
                new ScmPrecisionAllocator.Unit(UP.scale(6.0D),
                        new Vec3(0.0D, 0.0D, -6.0D), true),
                new ScmPrecisionAllocator.Unit(UP.scale(100.0D), Vec3.ZERO, false));
        double[] trim = ScmPrecisionAllocator.allocate(
                units, UP.scale(8.0D), Vec3.ZERO, null).controls();
        ScmAdaptiveStateModel model = model(List.of(
                new ScmAdaptiveStateModel.Actuator(UP.scale(1.5D),
                        new Vec3(0.0D, 0.0D, 1.5D), trim[0]),
                new ScmAdaptiveStateModel.Actuator(UP.scale(1.5D),
                        new Vec3(0.0D, 0.0D, -1.5D), trim[1]),
                new ScmAdaptiveStateModel.Actuator(UP.scale(25.0D), Vec3.ZERO, trim[2])));
        double height = 1.0D;
        double velocity = 0.0D;
        double[] previous = trim;
        for(int tick = 0; tick < 240; tick++){
            double correction = model.feedback(UP, false, -height, velocity).acceleration();
            ScmPrecisionAllocator.Allocation allocation = ScmPrecisionAllocator.allocate(
                    units, UP.scale(8.0D + 4.0D * correction), Vec3.ZERO, previous);
            previous = allocation.controls();
            double lift = 6.0D * (previous[0] + previous[1]) + 100.0D * previous[2];
            velocity += (lift - 8.0D) / 4.0D * 0.05D;
            height += velocity * 0.05D;
        }
        assertEquals(0.0D, height, 0.08D);
        assertEquals(0.0D, velocity, 0.08D);
        assertTrue(previous[0] > 0.2D);
        assertTrue(previous[1] > 0.2D);
        assertEquals(0.0D, previous[2], 1.0E-8D);
    }

    private static ScmAdaptiveStateModel model(List<ScmAdaptiveStateModel.Actuator> actuators){
        return ScmAdaptiveStateModel.sample(actuators, 0.05D, 0.35D,
                Math.toRadians(1.5D));
    }
}
