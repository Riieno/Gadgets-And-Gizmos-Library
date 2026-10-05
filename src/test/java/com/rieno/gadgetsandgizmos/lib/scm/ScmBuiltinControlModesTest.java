package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScmBuiltinControlModesTest {
    @Test
    void liveAirshipUsesItsAvailableCruiseAcceleration(){
        ScmAdaptiveStateModel model = ScmAdaptiveStateModel.sample(List.of(
                new ScmAdaptiveStateModel.Actuator(
                        new Vec3(0.0D, 0.0D, 20.0D), Vec3.ZERO, 0.0D),
                new ScmAdaptiveStateModel.Actuator(
                        new Vec3(0.0D, 0.0D, -20.0D), Vec3.ZERO, 0.0D)),
                0.05D, 0.75D, 0.05D);
        ScmControlMode mode = ScmControlModeRegistry.resolve("airship");
        ScmControlMode.ControlInput input = new ScmControlMode.ControlInput(
                Vec3.ZERO, Vec3.ZERO, Vec3.ZERO,
                new Vec3(0.0D, 0.0D, 1.0D),
                new Vec3(0.0D, 1.0D, 0.0D),
                new Vec3(1.0D, 0.0D, 0.0D),
                new Vec3(0.0D, 0.0D, 1000.0D),
                new Vec3(0.0D, 0.0D, 1.0D), Vec3.ZERO,
                28.0D, 0.75D, 1.0D, true,
                1000.0D, 1000.0D, 28.0D, 1.0D,
                true, true, false, 0.0D, 0.0D, model);
        Vec3 acceleration = mode.navigate(input).force();
        assertTrue(acceleration.z > 3.5D);
        ScmPrecisionAllocator.Allocation allocation =
                ScmPrecisionAllocator.allocateCruise(List.of(
                        new ScmPrecisionAllocator.Unit(
                                new Vec3(0.0D, 0.0D, 100.0D), Vec3.ZERO, false),
                        new ScmPrecisionAllocator.Unit(
                                new Vec3(0.0D, 0.0D, -100.0D), Vec3.ZERO, false)),
                        acceleration.scale(5.0D), Vec3.ZERO,
                        new Vec3(0.0D, 0.0D, 1.0D), null);
        assertTrue(allocation.controls()[0] > 0.95D);
        assertEquals(0.0D, allocation.controls()[1], 1.0E-8D);
    }

    @Test
    void gatesExperimentalIkRegistration(){
        ScmBuiltinControlModes.setIkEnabled(false);
        assertFalse(ScmControlModeRegistry.serializedIds().contains("ik"));
        assertEquals(ScmBuiltinControlModes.AIRSHIP_ID,
                ScmControlModeRegistry.resolve("ik").id());

        ScmBuiltinControlModes.setIkEnabled(true);
        assertTrue(ScmControlModeRegistry.serializedIds().contains("ik"));

        ScmBuiltinControlModes.setIkEnabled(false);
    }
}
