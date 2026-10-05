package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScmPrecisionAllocatorTest{
    private static final List<ScmPrecisionAllocator.Unit> UNITS = List.of(
            new ScmPrecisionAllocator.Unit(new Vec3(0.0D, 6.0D, 0.0D),
                    new Vec3(0.0D, 0.0D, 6.0D), true),
            new ScmPrecisionAllocator.Unit(new Vec3(0.0D, 6.0D, 0.0D),
                    new Vec3(0.0D, 0.0D, -6.0D), true),
            new ScmPrecisionAllocator.Unit(new Vec3(0.0D, 100.0D, 0.0D),
                    Vec3.ZERO, false));

    @Test
    void rcsHoldsLiftAndRotationWithoutFiringTheMainEngine(){
        ScmPrecisionAllocator.Allocation res = ScmPrecisionAllocator.allocate(
                UNITS, new Vec3(0.0D, 8.0D, 0.0D), Vec3.ZERO, null);
        double[] controls = res.controls();
        assertTrue(res.precisionOnly());
        assertEquals(0.0D, controls[2], 1.0E-8D);
        assertEquals(8.0D, 6.0D * (controls[0] + controls[1]), 0.25D);
        assertEquals(controls[0], controls[1], 0.01D);
        ScmPrecisionAllocator.Allocation next = ScmPrecisionAllocator.allocate(
                UNITS, new Vec3(0.0D, 8.0D, 0.0D), Vec3.ZERO, controls);
        assertEquals(controls[0], next.controls()[0], 0.03D);
    }

    @Test
    void mainEngineJoinsOnlyWhenRcsCannotSupplyTheRequestedLift(){
        ScmPrecisionAllocator.Allocation res = ScmPrecisionAllocator.allocate(
                UNITS, new Vec3(0.0D, 20.0D, 0.0D), Vec3.ZERO, null);
        assertFalse(res.precisionOnly());
        assertTrue(res.controls()[2] > 0.0D);
    }

    @Test
    void cruiseUsesMainPropulsionWhileRcsTrimsRotation(){
        List<ScmPrecisionAllocator.Unit> units = List.of(
                new ScmPrecisionAllocator.Unit(new Vec3(0.0D, 0.0D, 6.0D),
                        new Vec3(0.0D, 2.0D, 0.0D), true),
                new ScmPrecisionAllocator.Unit(new Vec3(0.0D, 0.0D, 6.0D),
                        new Vec3(0.0D, -2.0D, 0.0D), true),
                new ScmPrecisionAllocator.Unit(new Vec3(0.0D, 0.0D, 100.0D),
                        Vec3.ZERO, false));
        ScmPrecisionAllocator.Allocation res = ScmPrecisionAllocator.allocateCruise(
                units, new Vec3(0.0D, 0.0D, 8.0D),
                new Vec3(0.0D, 0.4D, 0.0D), null);
        assertFalse(res.precisionOnly());
        assertTrue(res.controls()[2] > 0.04D);
        assertTrue(res.controls()[0] > res.controls()[1]);
        ScmPrecisionAllocator.Allocation fromHover = ScmPrecisionAllocator.allocateCruise(
                units, new Vec3(0.0D, 0.0D, 8.0D),
                new Vec3(0.0D, 0.4D, 0.0D),
                ScmPrecisionAllocator.allocate(units,
                        new Vec3(0.0D, 0.0D, 8.0D), Vec3.ZERO, null).controls());
        assertTrue(fromHover.controls()[2] > 0.04D);
    }

    @Test
    void cruiseReleasesOpposingMainEngineFromPreviousControl(){
        List<ScmPrecisionAllocator.Unit> units = List.of(
                new ScmPrecisionAllocator.Unit(new Vec3(0.0D, 0.0D, 100.0D),
                        new Vec3(0.0D, 20.0D, 0.0D), false),
                new ScmPrecisionAllocator.Unit(new Vec3(0.0D, 0.0D, -100.0D),
                        new Vec3(0.0D, 20.0D, 0.0D), false),
                new ScmPrecisionAllocator.Unit(Vec3.ZERO,
                        new Vec3(0.0D, -30.0D, 0.0D), true));
        ScmPrecisionAllocator.Allocation res = ScmPrecisionAllocator.allocateCruise(
                units, new Vec3(0.0D, 0.0D, 80.0D), Vec3.ZERO,
                new Vec3(0.0D, 0.0D, 1.0D),
                new double[]{0.2D, 0.6D, 0.0D});
        assertEquals(0.0D, res.controls()[1], 1.0E-8D);
        assertTrue(res.controls()[0] > 0.7D);
        assertTrue(res.controls()[2] > 0.0D);
    }

    @Test
    void cruiseAllowsOpposingEnginesOnlyWhenNeededForTorque(){
        List<ScmPrecisionAllocator.Unit> units = List.of(
                new ScmPrecisionAllocator.Unit(new Vec3(0.0D, 0.0D, 100.0D),
                        new Vec3(0.0D, 20.0D, 0.0D), false),
                new ScmPrecisionAllocator.Unit(new Vec3(0.0D, 0.0D, -100.0D),
                        new Vec3(0.0D, 20.0D, 0.0D), false));
        ScmPrecisionAllocator.Allocation res = ScmPrecisionAllocator.allocateCruise(
                units, Vec3.ZERO, new Vec3(0.0D, 20.0D, 0.0D),
                new Vec3(0.0D, 0.0D, 1.0D), null);
        assertTrue(res.controls()[0] > 0.4D);
        assertTrue(res.controls()[1] > 0.4D);
    }

    @Test
    void largeOpposingProvidersHoldAConstantPartialThrottle(){
        List<ScmPrecisionAllocator.Unit> engines = List.of(
                new ScmPrecisionAllocator.Unit(new Vec3(0.0D, 100.0D, 0.0D),
                        Vec3.ZERO, false),
                new ScmPrecisionAllocator.Unit(new Vec3(0.0D, -100.0D, 0.0D),
                        Vec3.ZERO, false));
        ScmPrecisionAllocator.Allocation first = ScmPrecisionAllocator.allocate(
                engines, new Vec3(0.0D, 8.0D, 0.0D), Vec3.ZERO, null);
        ScmPrecisionAllocator.Allocation next = ScmPrecisionAllocator.allocate(
                engines, new Vec3(0.0D, 8.0D, 0.0D), Vec3.ZERO, first.controls());
        assertEquals(0.08D, next.controls()[0], 0.01D);
        assertEquals(0.0D, next.controls()[1], 0.01D);
        assertEquals(first.controls()[0], next.controls()[0], 0.01D);
    }

    @Test
    void normalizedAccelerationUsesMassAndLiveCapacity(){
        assertEquals(8.0D / 112.0D,
                ScmPrecisionAllocator.normalizedAcceleration(
                        UNITS, new Vec3(0.0D, 2.0D, 0.0D), 4.0D).y,
                1.0E-8D);
    }

    @Test
    void hoverSettlesWithContinuousRcsThrottle(){
        double height = 1.0D;
        double velocity = 0.0D;
        double[] previous = null;
        ScmStateFeedback.Gains gains = ScmStateFeedback.lqrForResponse(1.2D);
        for(int tick = 0; tick < 240; tick++){
            double correction = ScmStateFeedback.acceleration(-height, velocity, gains);
            double requestedLift = 8.0D + 4.0D * correction;
            ScmPrecisionAllocator.Allocation res = ScmPrecisionAllocator.allocate(
                    UNITS, new Vec3(0.0D, requestedLift, 0.0D), Vec3.ZERO, previous);
            previous = res.controls();
            double lift = 6.0D * (previous[0] + previous[1])
                    + 100.0D * previous[2];
            velocity += (lift - 8.0D) / 4.0D * 0.05D;
            height += velocity * 0.05D;
            if(tick > 80){
                assertTrue(previous[0] > 0.2D);
                assertTrue(previous[1] > 0.2D);
                assertEquals(0.0D, previous[2], 1.0E-8D);
            }
        }
        assertEquals(0.0D, height, 0.08D);
        assertEquals(0.0D, velocity, 0.08D);
    }
}
