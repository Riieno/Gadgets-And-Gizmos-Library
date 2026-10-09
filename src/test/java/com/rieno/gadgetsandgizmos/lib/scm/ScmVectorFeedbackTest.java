package com.rieno.gadgetsandgizmos.lib.scm;

import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyDynamicsApi.Tensor;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Preserve vector lift, leveling authority and supported descents in every craft frame
class ScmVectorFeedbackTest{
    private static final Tensor INVERSE = new Tensor(.025, 0, 0, 0, .025, 0, 0, 0, .025);

    @Test
    void sidewaysProvidersRetainLiftAndLevelingCapacityInEveryMountingFrame(){
        for(Direction forward : Direction.values()) for(Direction up : Direction.values()){
            if(forward.getAxis() == up.getAxis()) continue;
            var frame = new ScmOrientation(forward, up);
            Vec3 force = frame.forwardVector().scale(100);
            Vec3 arm = frame.forwardVector().scale(2);
            var units = List.of(new ScmPrecisionAllocator.Unit(force, Vec3.ZERO, false, arm, 30),
                    new ScmPrecisionAllocator.Unit(force.scale(-1), Vec3.ZERO, false, arm.scale(-1), 30));
            Vec3 lift = ScmPrecisionAllocator.physicalForce(units, frame.upVector());
            assertEquals(100, lift.dot(frame.upVector()), 1.0E-8);
            Vec3 correction = ScmPrecisionAllocator.normalizedAcceleration(units, frame.upVector().scale(2), 10);
            assertEquals(20, ScmPrecisionAllocator.physicalForce(units, correction).dot(frame.upVector()), 1.0E-8);
            assertEquals(200, ScmPrecisionAllocator.physicalTorque(units, frame.rightVector()).dot(frame.rightVector()), 1.0E-8);
            List<ScmAdaptiveStateModel.Actuator> actuators = new ArrayList<>();
            units.forEach(unit -> actuators.addAll(ScmAdaptiveStateModel.forceActuators(unit, 10, INVERSE, 0)));
            var model = ScmAdaptiveStateModel.sample(actuators, .05, .1, .02, 2);
            assertTrue(model.authority(frame.upVector(), false, true) > 1);
            assertTrue(model.authority(frame.upVector(), false, false) > 1);
            assertTrue(model.authority(frame.rightVector(), true, true) > 1);
            assertTrue(model.authority(frame.rightVector(), true, false) > 1);
        }
    }

    @Test
    void liftOnlyProvidersDescendByReducingTheirHoldingForce(){
        Vec3 up = new Vec3(0, 1, 0);
        var units = List.of(new ScmPrecisionAllocator.Unit(up.scale(100), Vec3.ZERO, false));
        Vec3 hold = up.scale(40);
        Vec3 normalized = ScmPrecisionAllocator.normalizedAcceleration(units, up.scale(-2), 10, hold);
        Vec3 physical = ScmPrecisionAllocator.physicalForce(units, normalized, hold).add(hold);
        assertEquals(20, physical.y, 1.0E-8);
        assertEquals(0, ScmPrecisionAllocator.physicalForce(units, up.scale(-1)).lengthSqr(), 1.0E-8);
        assertEquals(0, ScmPrecisionAllocator.physicalForce(units, up.scale(-1), hold).add(hold).y, 1.0E-8);
    }

    @Test
    void liveConeChangesPreserveFixedProviderCompatibility(){
        Vec3 force = new Vec3(100, 0, 0);
        Vec3 up = new Vec3(0, 1, 0);
        assertEquals(0, ScmThrustGeometry.maximumProjection(force, 0, up), 1.0E-8);
        assertEquals(50, ScmThrustGeometry.maximumProjection(force, 30, up), 1.0E-8);
        assertEquals(100, ScmThrustGeometry.maximumProjection(force, 90, up), 1.0E-8);
        assertEquals(0, ScmThrustGeometry.maximumProjection(force, 30, force.scale(-1)), 1.0E-8);
        assertTrue(ScmThrustGeometry.steeringForces(force, 0).isEmpty());
        assertEquals(0, ScmThrustGeometry.maximumProjection(null, 30, up));
        assertEquals(0, ScmThrustGeometry.maximumProjection(force, 30, new Vec3(Double.NaN, 0, 0)));
        assertTrue(ScmThrustGeometry.steeringForces(new Vec3(Double.NaN, 0, 0), 30).isEmpty());
        assertEquals(1, ScmAdaptiveStateModel.forceActuators(
                new ScmPrecisionAllocator.Unit(force, Vec3.ZERO, false), 10, INVERSE, 0).size());
    }

    @Test
    void levelingHandlesInversionAndYieldsOnlyDrivenAxes(){
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 forward = new Vec3(0, 0, -1);
        assertEquals(Vec3.ZERO, ScmControlAxes.uprightError(up, up, forward));
        assertEquals(Math.PI, ScmControlAxes.uprightError(up.scale(-1), up, forward).length(), 1.0E-8);
        assertEquals(Math.PI / 2, ScmControlAxes.uprightError(new Vec3(1, 0, 0), up, forward).z, 1.0E-8);
        Vec3 leveling = new Vec3(2, 0, 3);
        assertEquals(leveling, ScmControlPriority.remainingTorque(leveling, up.scale(4), forward, up));
        assertEquals(new Vec3(0, 0, 3), ScmControlPriority.remainingTorque(leveling, new Vec3(-4, 0, 0), forward, up));
        assertEquals(new Vec3(2, 0, 0), ScmControlPriority.remainingTorque(leveling, forward.scale(4), forward, up));
        assertEquals(leveling, ScmControlPriority.remainingTorque(leveling, Vec3.ZERO, forward, up));
    }

    @Test
    void descendingKeepsHoldingPropulsionReachableInEveryCraftFrame(){
        for(Direction forward : Direction.values()) for(Direction up : Direction.values()){
            if(forward.getAxis() == up.getAxis()) continue;
            var frame = new ScmOrientation(forward, up);
            var actions = ScmControlAxes.withSupportActions(java.util.Set.of("ship_descend"),
                    frame.upVector().scale(40), frame.upVector());
            assertTrue(actions.containsAll(java.util.Set.of("ship_ascend", "ship_descend")));
            var inverted = ScmControlAxes.withSupportActions(java.util.Set.of("ship_ascend"),
                    frame.upVector().scale(-40), frame.upVector());
            assertTrue(inverted.containsAll(java.util.Set.of("ship_ascend", "ship_descend")));
        }
    }
}
