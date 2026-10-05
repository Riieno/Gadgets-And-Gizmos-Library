package com.rieno.gadgetsandgizmos.lib.scm;

import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyDynamicsApi.Tensor;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScmArticulatedFlightControlTest{
    private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);

    @Test
    void carriagesSupportTheirOwnMassWithoutCreatingPitchOrRoll(){
        List<ScmArticulatedFlightControl.Carriage> carriages = List.of(
                carriage(true, 1.0D, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, UP, units()),
                carriage(false, 3.0D, new Vec3(10.0D, 0.0D, 0.0D),
                        Vec3.ZERO, Vec3.ZERO, UP, units()));
        Vec3 hold = UP.scale(40.0D);
        List<ScmArticulatedFlightControl.Request> requests = requests(carriages, hold, hold);
        assertVector(UP.scale(10.0D), requests.get(0).force());
        assertVector(UP.scale(30.0D), requests.get(1).force());
        assertVector(Vec3.ZERO, requests.get(0).torque());
        assertVector(Vec3.ZERO, requests.get(1).torque());
    }

    @Test
    void followerTiltAndYawRateGetLocalFeedback(){
        var primary = carriage(true, 1.0D, Vec3.ZERO,
                Vec3.ZERO, Vec3.ZERO, UP, units());
        var tilted = carriage(false, 1.0D, new Vec3(10.0D, 0.0D, 0.0D),
                Vec3.ZERO, Vec3.ZERO, new Vec3(0.2D, 1.0D, 0.0D), units());
        var tiltRequests = requests(List.of(primary, tilted), Vec3.ZERO, Vec3.ZERO);
        assertTrue(tiltRequests.get(1).torque().z > 0.0D);
        assertVector(Vec3.ZERO, tiltRequests.get(0).torque());
        var turning = carriage(false, 1.0D, tilted.center(),
                Vec3.ZERO, UP.scale(0.5D), UP, units());
        var yawRequests = requests(List.of(primary, turning), Vec3.ZERO, Vec3.ZERO);
        assertTrue(yawRequests.get(1).torque().y < 0.0D);
    }

    @Test
    void separationDampingPreservesSharedTranslation(){
        Vec3 axis = new Vec3(1.0D, 0.0D, 0.0D);
        var primary = carriage(true, 1.0D, Vec3.ZERO,
                axis, Vec3.ZERO, UP, units());
        var follower = carriage(false, 3.0D, new Vec3(10.0D, 0.0D, 0.0D),
                axis.scale(-1.0D), Vec3.ZERO, UP, units());
        var requests = requests(List.of(primary, follower), Vec3.ZERO, Vec3.ZERO);
        assertTrue(requests.get(0).force().x < 0.0D);
        assertTrue(requests.get(1).force().x > 0.0D);
        assertVector(Vec3.ZERO, requests.get(0).force().add(requests.get(1).force()));
    }

    @Test
    void freeTransverseSwayDoesNotCommandRigidFormationForces(){
        var primary = carriage(true, 1.0D, Vec3.ZERO,
                new Vec3(0, 0, 2), UP.scale(0.5D), UP, units());
        var follower = carriage(false, 3.0D, new Vec3(10, 0, 0),
                new Vec3(0, 0, -2), UP.scale(0.5D), UP, units());
        var requests = requests(List.of(primary, follower), Vec3.ZERO, Vec3.ZERO);
        assertVector(Vec3.ZERO, requests.get(0).force());
        assertVector(Vec3.ZERO, requests.get(1).force());
        assertVector(Vec3.ZERO, requests.get(0).torque());
        assertVector(Vec3.ZERO, requests.get(1).torque());
    }

    @Test
    void disturbedLeadDoesNotTiltTheFollowers(){
        var primary = carriage(true, 1.0D, Vec3.ZERO,
                Vec3.ZERO, Vec3.ZERO, new Vec3(1, 1, 0), units());
        var follower = carriage(false, 1.0D, new Vec3(10, 0, 0),
                Vec3.ZERO, Vec3.ZERO, UP, units());
        var requests = requests(List.of(primary, follower), UP.scale(20), UP.scale(20));
        assertVector(Vec3.ZERO, requests.get(1).torque());
    }

    @Test
    void repeatedYawDisturbancesLoseEnergyWithoutChangingTotalAngularMomentum(){
        List<ScmArticulatedFlightControl.Carriage> carriages = List.of(
                carriage(true, 1, Vec3.ZERO, Vec3.ZERO, UP.scale(2), UP, units()),
                carriage(false, 2, new Vec3(10, 0, 0), Vec3.ZERO, UP.scale(-1), UP, units()),
                carriage(false, 3, new Vec3(20, 0, 0), Vec3.ZERO, UP.scale(0.5D), UP, units()));
        var connections = List.of(new ScmArticulatedFlightControl.Connection(carriages.get(0).id(), carriages.get(1).id()),
                new ScmArticulatedFlightControl.Connection(carriages.get(1).id(), carriages.get(2).id()));
        double momentum = 1.5D;
        double previousEnergy = Double.POSITIVE_INFINITY;
        for(int tick = 0; tick < 120; tick++){
            var requests = ScmArticulatedFlightControl.requests(carriages, connections,
                    Vec3.ZERO, Vec3.ZERO, UP.scale(60), 0.05D, 0.25D, Math.toRadians(2));
            List<ScmArticulatedFlightControl.Carriage> next = new ArrayList<>();
            double energy = 0;
            double total = 0;
            for(int idx = 0; idx < carriages.size(); idx++){
                var carriage = carriages.get(idx);
                Vec3 rate = carriage.angularVelocity().add(carriage.inertia().inverse()
                        .transform(requests.get(idx).torque()).scale(0.05D));
                next.add(new ScmArticulatedFlightControl.Carriage(carriage.id(), carriage.primary(), carriage.mass(),
                        carriage.inertia(), carriage.center(), carriage.velocity(), rate, UP, carriage.units()));
                energy += 0.5D * carriage.mass() * rate.lengthSqr();
                total += carriage.mass() * rate.y;
            }
            assertTrue(energy <= previousEnergy + 1.0E-9D);
            assertEquals(momentum, total, 1.0E-8D);
            previousEnergy = energy;
            carriages = next;
        }
        assertEquals(0.25D, carriages.get(0).angularVelocity().y, 1.0E-3D);
        assertEquals(0.25D, carriages.get(2).angularVelocity().y, 1.0E-3D);
    }

    @Test
    void followerThrustCannotPretendToRotateThePrimaryBody(){
        var primary = carriage(true, 1.0D, Vec3.ZERO,
                Vec3.ZERO, Vec3.ZERO, UP, units());
        var follower = carriage(false, 2.0D, new Vec3(20.0D, 0.0D, 0.0D),
                Vec3.ZERO, Vec3.ZERO, UP, units());
        var single = ScmArticulatedFlightControl.navigationActuators(List.of(primary), Vec3.ZERO);
        var combined = ScmArticulatedFlightControl.navigationActuators(
                List.of(primary, follower), Vec3.ZERO);
        for(int idx = 0; idx < single.size(); idx++){
            assertVector(single.get(idx).angularAcceleration(), combined.get(idx).angularAcceleration());
            assertVector(single.get(idx).linearAcceleration().scale(1.0D / 3.0D),
                    combined.get(idx).linearAcceleration());
        }
        for(int idx = single.size(); idx < combined.size(); idx++){
            assertVector(Vec3.ZERO, combined.get(idx).angularAcceleration());
        }
    }

    @Test
    void poweredSectionsCarryUnpoweredCarriagesAndBalanceTheirLever(){
        var primary = carriage(true, 1.0D, Vec3.ZERO,
                Vec3.ZERO, Vec3.ZERO, UP, units());
        var cargo = carriage(false, 1.0D, new Vec3(5.0D, 0.0D, 0.0D),
                Vec3.ZERO, Vec3.ZERO, UP, List.of());
        Vec3 force = UP.scale(20.0D);
        var carriages = List.of(primary, cargo);
        var allocated = ScmArticulatedFlightControl.allocate(carriages,
                requests(carriages, force, force), null, Vec3.ZERO);
        Vec3 achieved = Vec3.ZERO;
        Vec3 torque = Vec3.ZERO;
        double[] controls = allocated.get(0).controls();
        for(int idx = 0; idx < primary.units().size(); idx++){
            var unit = primary.units().get(idx);
            achieved = achieved.add(unit.force().scale(controls[idx]));
            torque = torque.add(unit.torque().scale(controls[idx]));
        }
        assertEquals(20.0D, achieved.y, 0.7D);
        assertEquals(50.0D, torque.z, 2.0D);
        assertEquals(0, allocated.get(1).controls().length);
    }

    // Use independent force and torque channels to isolate carriage feedback
    private static List<ScmPrecisionAllocator.Unit> units(){
        List<ScmPrecisionAllocator.Unit> units = new ArrayList<>();
        for(Vec3 axis : List.of(new Vec3(1.0D, 0.0D, 0.0D), UP,
                new Vec3(0.0D, 0.0D, 1.0D))){
            for(double sign : new double[]{-1.0D, 1.0D}){
                Vec3 effect = axis.scale(100.0D * sign);
                units.add(new ScmPrecisionAllocator.Unit(effect, Vec3.ZERO, true));
                units.add(new ScmPrecisionAllocator.Unit(Vec3.ZERO, effect, true));
            }
        }
        return units;
    }

    // Build one synthetic carriage with a known inertia
    private static ScmArticulatedFlightControl.Carriage carriage(boolean primary,
            double mass, Vec3 center, Vec3 velocity, Vec3 rate, Vec3 up,
            List<ScmPrecisionAllocator.Unit> units){
        Tensor inertia = new Tensor(mass, 0, 0, 0, mass, 0, 0, 0, mass);
        return new ScmArticulatedFlightControl.Carriage(UUID.randomUUID(), primary,
                mass, inertia, center, velocity, rate, up, units);
    }

    // Evaluate the common control interval and capture tolerances
    private static List<ScmArticulatedFlightControl.Request> requests(
            List<ScmArticulatedFlightControl.Carriage> carriages, Vec3 force, Vec3 hold){
        return ScmArticulatedFlightControl.requests(carriages, force, Vec3.ZERO,
                hold, 0.05D, 0.25D, Math.toRadians(2.0D));
    }

    // Compare the complete physical vector
    private static void assertVector(Vec3 expected, Vec3 actual){
        assertEquals(expected.x, actual.x, 1.0E-8D);
        assertEquals(expected.y, actual.y, 1.0E-8D);
        assertEquals(expected.z, actual.z, 1.0E-8D);
    }
}
