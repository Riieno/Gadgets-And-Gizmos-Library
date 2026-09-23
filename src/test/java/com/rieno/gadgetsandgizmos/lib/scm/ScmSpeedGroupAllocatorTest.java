package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScmSpeedGroupAllocatorTest{
    private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);

    @Test
    void positiveLowPowerDriveProducesARealSignal(){
        assertEquals(1, ScmSpeedControl.quantizedSignal(0.03D, 15));
        assertEquals(8, ScmSpeedControl.quantizedSignal(0.03D, 256));
        assertEquals(15, ScmSpeedControl.quantizedSignal(4.0D, 15));
        for(double val : new double[]{0.0D, -0.1D, Double.NaN, Double.POSITIVE_INFINITY}){
            assertEquals(0, ScmSpeedControl.quantizedSignal(val, 15));
        }
        assertEquals(0, ScmSpeedControl.quantizedSignal(1.0D, 0));
    }

    @Test
    void signalBoundaryJitterDoesNotPulsePowerOrRebuildKinetics(){
        assertEquals(6, ScmSpeedControl.quantizedSignal(6.55D / 15.0D, 15, 6, 0.15D));
        assertEquals(7, ScmSpeedControl.quantizedSignal(6.75D / 15.0D, 15, 6, 0.15D));
        assertEquals(7, ScmSpeedControl.quantizedSignal(6.45D / 15.0D, 15, 7, 0.15D));
        assertEquals(6, ScmSpeedControl.quantizedSignal(6.25D / 15.0D, 15, 7, 0.15D));
        assertEquals(0, ScmSpeedControl.quantizedSignal(0.0D, 15, 6, 0.15D));
        assertEquals(1, ScmSpeedControl.quantizedSignal(0.03D, 15, 0, 0.15D));
    }

    @Test
    void independentRotorsBalanceTheirDifferentForceAndLeverArms(){
        var influences = List.of(
                new ScmSpeedGroupAllocator.Influence(UP.scale(100.0D), new Vec3(0.0D, 0.0D, -100.0D)),
                new ScmSpeedGroupAllocator.Influence(UP.scale(200.0D), new Vec3(0.0D, 0.0D, 200.0D)));
        Vec3 support = ScmSpeedGroupAllocator.normalizePhysicalForce(influences, UP.scale(120.0D));
        double[] res = ScmSpeedGroupAllocator.allocate(influences, support, Vec3.ZERO, 1.0D);
        assertEquals(0.6D, res[0], 1.0E-4D);
        assertEquals(0.3D, res[1], 1.0E-4D);
        assertEquals(120.0D, res[0] * 100.0D + res[1] * 200.0D, 0.01D);
        assertEquals(0.0D, -res[0] * 100.0D + res[1] * 200.0D, 0.01D);
    }

    @Test
    void travelAndLiftDoNotShareOneOnOffSignal(){
        var influences = List.of(
                new ScmSpeedGroupAllocator.Influence(UP.scale(100.0D), Vec3.ZERO),
                new ScmSpeedGroupAllocator.Influence(new Vec3(0.0D, 0.0D, 100.0D), Vec3.ZERO));
        double[] res = ScmSpeedGroupAllocator.allocate(influences, new Vec3(0.0D, 0.4D, 0.03D), Vec3.ZERO, 1.0D);
        assertEquals(0.4D, res[0], 1.0E-5D);
        assertEquals(0.03D, res[1], 1.0E-5D);
        assertEquals(6, ScmSpeedControl.quantizedSignal(res[0], 15));
        assertEquals(1, ScmSpeedControl.quantizedSignal(res[1], 15));
        assertArrayEquals(new double[]{0.0D, 0.0D},
                ScmSpeedGroupAllocator.allocate(influences, UP, UP, 0.0D));
    }

    @Test
    void equivalentControllersShareTheLoadRatherThanOnlyPoweringTheFirstBlock(){
        var influence = new ScmSpeedGroupAllocator.Influence(new Vec3(0.0D, 0.0D, 100.0D), Vec3.ZERO);
        var influences = java.util.Collections.nCopies(27, influence);
        double[] res = ScmSpeedGroupAllocator.allocate(influences,
                new Vec3(0.0D, 0.0D, 0.03D), Vec3.ZERO, 1.0D);
        for(double control : res){
            assertEquals(0.03D, control, 1.0E-5D);
            assertEquals(1, ScmSpeedControl.quantizedSignal(control, 15));
        }
    }

    @Test
    void ownMeasuredCurveConvertsLiftEffortToSpeedWithoutUsingZeroControlBaseline(){
        var samples = List.of(new ScmSpeedGroupAllocator.Sample(0.0D, 30.0D),
                new ScmSpeedGroupAllocator.Sample(0.5D, 55.0D),
                new ScmSpeedGroupAllocator.Sample(1.0D, 130.0D));
        assertEquals(0.5D, ScmSpeedGroupAllocator.controlForEffort(samples, 0.25D), 1.0E-9D);
        assertEquals(0.0D, ScmSpeedGroupAllocator.controlForEffort(samples, 0.0D));
        assertEquals(1.0D, ScmSpeedGroupAllocator.controlForEffort(samples, 1.0D));
        assertEquals(0.03D, ScmSpeedGroupAllocator.controlForEffort(List.of(), 0.03D));
    }

    @Test
    void flightOutputChangesGraduallyAndRemainsPositive(){
        double control = 0.0D;
        for(int idx = 0; idx < 10; idx++){
            control = ScmSpeedGroupAllocator.regulatedControl(control, 0.65D, 0.1D);
            assertTrue(control > 0.0D && control <= 0.65D);
        }
        assertEquals(0.65D, control, 1.0E-9D);
        assertEquals(0.55D, ScmSpeedGroupAllocator.regulatedControl(control, 0.0D, 0.1D), 1.0E-9D);
        assertEquals(0.0D, ScmSpeedGroupAllocator.regulatedControl(Double.NaN, -1.0D, 0.1D));
    }
}
