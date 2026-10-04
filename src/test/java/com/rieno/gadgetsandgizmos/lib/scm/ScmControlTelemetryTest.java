package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScmControlTelemetryTest {
    @Test
    void projectsCorrectionsIntoControllerAxesWithRightPositiveYaw() {
        ScmControlTelemetry telemetry = ScmControlTelemetry.fromVectors(
                new Vec3(0.25D, 0.5D, 0.75D),
                new Vec3(0.4D, -0.3D, 0.2D),
                Vec3.ZERO, Vec3.ZERO,
                new Vec3(0.0D, 0.0D, 1.0D),
                new Vec3(0.0D, 1.0D, 0.0D),
                0.0D, 0.0D, 0.0D, 0.0D);

        assertEquals(-0.4D, telemetry.correction().pitch(), 1.0E-9D);
        assertEquals(0.3D, telemetry.correction().yaw(), 1.0E-9D);
        assertEquals(0.2D, telemetry.correction().roll(), 1.0E-9D);
        assertEquals(0.75D, telemetry.correction().throttle(), 1.0E-9D);
        assertEquals(-0.25D, telemetry.correction().strafe(), 1.0E-9D);
        assertEquals(0.5D, telemetry.correction().lift(), 1.0E-9D);
        assertEquals(ScmControlTelemetry.Axes.ZERO, telemetry.demand());
    }
}
