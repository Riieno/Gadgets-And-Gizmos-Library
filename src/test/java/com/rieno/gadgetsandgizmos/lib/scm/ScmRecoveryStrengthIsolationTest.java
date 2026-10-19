package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Keep independent recovery gains on the axes owned by each control
class ScmRecoveryStrengthIsolationTest{
    // Leave height recovery to its owner while braking both horizontal components
    @Test void heightOwnershipPreservesHorizontalBraking(){
        Vec3 velocity = new Vec3(2, -3, 4);
        Vec3 stopping = ScmControlPriority.remainingForce(velocity, new Vec3(0, 1, 0));
        assertEquals(new Vec3(2, 0, 4), stopping);
        for(double strength : new double[]{0.25D, 0.5D, 1.0D}){
            Vec3 full = ScmMotionDamping.stopLinear(velocity, strength, 0.05D);
            Vec3 horizontal = ScmMotionDamping.stopLinear(stopping, strength, 0.05D);
            assertEquals(full.x, horizontal.x, 1.0E-9D);
            assertEquals(full.z, horizontal.z, 1.0E-9D);
            assertEquals(0, horizontal.y, 1.0E-9D);
        }
    }

    // Respect the supplied world-height direction when the ship's local frame is tilted
    @Test void protectedAxesUseTheCallersCoordinateFrame(){
        Vec3 axis = new Vec3(1, 2, -3).normalize();
        Vec3 horizontal = axis.cross(new Vec3(1, 0, 0)).normalize().scale(4);
        Vec3 velocity = horizontal.add(axis.scale(-2));
        Vec3 remaining = ScmControlPriority.remainingForce(velocity, axis.scale(7));
        assertEquals(0, horizontal.distanceTo(remaining), 1.0E-9D);
        assertEquals(0, remaining.dot(axis), 1.0E-9D);
    }

    // Keep three-axis braking available when no height control is enabled
    @Test void noOwnedAxisKeepsTheEntireRecoveryRequest(){
        Vec3 velocity = new Vec3(2, -3, 4);
        assertSame(velocity, ScmControlPriority.remainingForce(velocity, Vec3.ZERO));
        assertEquals(Vec3.ZERO, ScmControlPriority.remainingForce(Vec3.ZERO, new Vec3(0, 1, 0)));
    }

    // An explicit stabilization node selects its gain regardless of the hover setting
    @Test void stabilizationStrengthIsIndependentOfHoverStrength(){
        for(double hover : new double[]{0, 0.25D, 0.5D, 1.0D}){
            for(double stabilize : new double[]{0.1D, 0.25D, 0.5D, 1.0D}){
                assertEquals(stabilize, ScmControlPriority.uprightRecoveryStrength(hover, stabilize));
            }
            assertEquals(hover, ScmControlPriority.uprightRecoveryStrength(hover, 0));
        }
        assertEquals(0.5D, ScmControlPriority.uprightRecoveryStrength(0.5D, Double.NaN));
        assertEquals(1, ScmControlPriority.uprightRecoveryStrength(0.5D, 2));
        assertEquals(0, ScmControlPriority.uprightRecoveryStrength(Double.NaN, 0));
    }
}
