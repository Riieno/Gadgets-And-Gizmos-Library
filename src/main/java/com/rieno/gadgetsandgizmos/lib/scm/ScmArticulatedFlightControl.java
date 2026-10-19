package com.rieno.gadgetsandgizmos.lib.scm;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyDynamicsApi.Tensor;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Balance a flying assembly without treating its free joints as one rigid body
public final class ScmArticulatedFlightControl{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Keep the shared control API stateless
    private ScmArticulatedFlightControl(){}

    // Describe one carriage in the common control frame, with torque about its own centre
    public record Carriage(UUID id, boolean primary, double mass, Tensor inertia,
                           Vec3 center, Vec3 velocity, Vec3 angularVelocity, Vec3 up,
                           List<ScmPrecisionAllocator.Unit> units){
        public Carriage{
            if(id == null || !Double.isFinite(mass) || mass <= 0.0D){
                throw new IllegalArgumentException("Carriages require an identity and positive mass");
            }
            inertia = inertia == null ? Tensor.ZERO : inertia;
            center = finite(center);
            velocity = finite(velocity);
            angularVelocity = finite(angularVelocity);
            up = finite(up);
            units = units == null ? List.of() : List.copyOf(units);
        }
    }

    // Store physical targets in the same order as the supplied carriages
    public record Request(UUID id, Vec3 force, Vec3 torque){}

    // Share unsupported load through the assembly when a carriage lacks sufficient propulsion
    public static List<ScmPrecisionAllocator.Allocation> allocate(
            List<Carriage> carriages, List<Request> requests,
            List<double[]> previous, Vec3 cruiseDirection){
        if(carriages == null || requests == null || carriages.size() != requests.size()){
            throw new IllegalArgumentException("Carriages and requests must have the same order and size");
        }
        if(carriages.isEmpty()) return List.of();
        Vec3 targetForce = Vec3.ZERO;
        List<Vec3> torques = new ArrayList<>();
        List<ScmPrecisionAllocator.Allocation> res = new ArrayList<>();
        for(int idx = 0; idx < carriages.size(); idx++){
            Carriage carriage = carriages.get(idx);
            Request request = requests.get(idx);
            if(!carriage.id().equals(request.id())) throw new IllegalArgumentException("Carriage request identities must match");
            targetForce = targetForce.add(request.force());
            torques.add(request.torque());
        }
        // Unpowered cargo transfers its pitch and roll load through the nearest supporting section.
        for(int idx = 0; idx < carriages.size(); idx++){
            Carriage cargo = carriages.get(idx);
            if(!cargo.units().isEmpty()) continue;
            int support = -1;
            double distance = Double.POSITIVE_INFINITY;
            for(int candidate = 0; candidate < carriages.size(); candidate++){
                Carriage carriage = carriages.get(candidate);
                double separation = carriage.center().distanceToSqr(cargo.center());
                if(!carriage.units().isEmpty() && separation < distance){
                    support = candidate;
                    distance = separation;
                }
            }
            if(support < 0) continue;
            Carriage carriage = carriages.get(support);
            Vec3 transferred = cargo.center().subtract(carriage.center()).cross(requests.get(idx).force());
            Vec3 up = carriage.up().normalize();
            transferred = transferred.subtract(up.scale(transferred.dot(up)));
            torques.set(support, torques.get(support).add(transferred));
        }
        for(int idx = 0; idx < carriages.size(); idx++){
            double[] prev = previous != null && idx < previous.size() ? previous.get(idx) : null;
            res.add(allocate(carriages.get(idx).units(), requests.get(idx).force(),
                    torques.get(idx), cruiseDirection, prev));
        }
        // Share missing force while retaining each carriage's own attitude target.
        double previousError = Double.POSITIVE_INFINITY;
        for(int pass = 0; pass < 8; pass++){
            List<Vec3> achieved = new ArrayList<>();
            Vec3 sum = Vec3.ZERO;
            for(int idx = 0; idx < carriages.size(); idx++){
                Vec3 force = achievedForce(carriages.get(idx), res.get(idx).controls());
                achieved.add(force);
                sum = sum.add(force);
            }
            Vec3 error = targetForce.subtract(sum);
            if(error.lengthSqr() <= Math.max(1.0E-12D, targetForce.lengthSqr() * 0.0009D)) break;
            double squaredError = error.lengthSqr();
            if(pass > 0 && previousError - squaredError <= Math.max(1.0E-12D, previousError * 1.0E-6D)) break;
            previousError = squaredError;
            Vec3 direction = error.normalize();
            double[] reserve = new double[carriages.size()];
            double total = 0.0D;
            for(int idx = 0; idx < carriages.size(); idx++){
                Carriage carriage = carriages.get(idx);
                double[] controls = res.get(idx).controls();
                for(int unitIdx = 0; unitIdx < carriage.units().size(); unitIdx++){
                    double effect = carriage.units().get(unitIdx).force().dot(direction);
                    reserve[idx] += effect >= 0.0D ? effect * (1.0D - controls[unitIdx]) : -effect * controls[unitIdx];
                }
                total += reserve[idx];
            }
            if(total <= 1.0E-9D) break;
            for(int idx = 0; idx < carriages.size(); idx++){
                if(reserve[idx] <= 1.0E-9D) continue;
                res.set(idx, allocate(carriages.get(idx).units(),
                        achieved.get(idx).add(error.scale(reserve[idx] / total)), torques.get(idx),
                        cruiseDirection, res.get(idx).controls()));
            }
        }
        return List.copyOf(res);
    }

    // Sum one section's actual force from its allocated controls
    private static Vec3 achievedForce(Carriage carriage, double[] controls){
        Vec3 force = Vec3.ZERO;
        for(int idx = 0; idx < carriage.units().size(); idx++){
            force = force.add(carriage.units().get(idx).force().scale(controls[idx]));
        }
        return force;
    }

    // Model shared translation and the primary carriage's measured rotation
    public static List<ScmAdaptiveStateModel.Actuator> navigationActuators(
            List<Carriage> carriages, Vec3 holdingForce){
        if(carriages == null || carriages.isEmpty()) return List.of();
        double mass = totalMass(carriages);
        List<Request> holds = carriages.stream().map(carriage -> new Request(carriage.id(),
                finite(holdingForce).scale(carriage.mass() / mass), Vec3.ZERO)).toList();
        List<ScmPrecisionAllocator.Allocation> holding = allocate(carriages, holds, null, Vec3.ZERO);
        List<ScmAdaptiveStateModel.Actuator> res = new ArrayList<>();
        for(int carriageIdx = 0; carriageIdx < carriages.size(); carriageIdx++){
            Carriage carriage = carriages.get(carriageIdx);
            double[] trim = holding.get(carriageIdx).controls();
            for(int idx = 0; idx < carriage.units().size(); idx++){
                ScmPrecisionAllocator.Unit unit = carriage.units().get(idx);
                res.addAll(ScmAdaptiveStateModel.forceActuators(unit, mass,
                        carriage.primary() ? carriage.inertia().inverse() : Tensor.ZERO, trim[idx]));
            }
        }
        return List.copyOf(res);
    }

    // Describe the free coupler between two carriage identities
    public record Connection(UUID first, UUID second){}

    // Use direct primary connections when the host has no joint graph
    public static List<Request> requests(List<Carriage> carriages, Vec3 requestedForce,
                                          Vec3 primaryTorque, Vec3 holdingForce,
                                          double tickSeconds, double linearTolerance,
                                          double angularTolerance){
        if(carriages == null || carriages.isEmpty()) return List.of();
        Carriage primary = carriages.stream().filter(Carriage::primary).findFirst().orElseThrow();
        List<Connection> connections = carriages.stream().filter(carriage -> !carriage.primary())
                .map(carriage -> new Connection(primary.id(), carriage.id())).toList();
        return requests(carriages, connections, requestedForce, primaryTorque, holdingForce,
                tickSeconds, linearTolerance, angularTolerance);
    }

    // Support each mass while damping joint motion without imposing a rigid formation
    public static List<Request> requests(List<Carriage> carriages, List<Connection> connections,
                                          Vec3 requestedForce, Vec3 primaryTorque, Vec3 holdingForce,
                                          double tickSeconds, double linearTolerance,
                                          double angularTolerance){
        if(carriages == null || carriages.isEmpty()) return List.of();
        Carriage primary = carriages.stream().filter(Carriage::primary).findFirst().orElseThrow();
        double mass = totalMass(carriages);
        Vec3 acceleration = finite(requestedForce).scale(1.0D / mass);
        Vec3 vertical = finite(holdingForce).normalize();
        if(vertical.lengthSqr() <= 1.0E-12D) vertical = primary.up().normalize();
        List<ScmAdaptiveStateModel> models = new ArrayList<>();
        List<Vec3> forces = new ArrayList<>();
        List<Vec3> torques = new ArrayList<>();
        for(Carriage carriage : carriages){
            ScmAdaptiveStateModel model = model(carriage, finite(holdingForce).scale(carriage.mass() / mass),
                    tickSeconds, linearTolerance, angularTolerance);
            models.add(model);
            forces.add(acceleration.scale(carriage.mass()));
            // Gravity supplies the follower tilt reference even when the lead cabin is disturbed.
            Vec3 error = carriage.up().normalize().cross(vertical);
            Vec3 tiltRate = carriage.angularVelocity().subtract(
                    vertical.scale(carriage.angularVelocity().dot(vertical)));
            torques.add(carriage.primary() ? finite(primaryTorque)
                    : carriage.inertia().transform(model.angularFeedback(error, tiltRate)));
        }
        if(connections != null){
            java.util.Map<UUID, java.util.Set<UUID>> neighbours = new java.util.HashMap<>();
            for(Connection connection : connections){
                if(connection == null || connection.first() == null || connection.second() == null
                        || connection.first().equals(connection.second())
                        || index(carriages, connection.first()) < 0 || index(carriages, connection.second()) < 0) continue;
                neighbours.computeIfAbsent(connection.first(), ignored -> new java.util.HashSet<>()).add(connection.second());
                neighbours.computeIfAbsent(connection.second(), ignored -> new java.util.HashSet<>()).add(connection.first());
            }
            java.util.Set<java.util.Set<UUID>> seen = new java.util.HashSet<>();
            for(Connection connection : connections){
                if(connection == null || connection.first() == null || connection.second() == null
                        || connection.first().equals(connection.second())
                        || !seen.add(java.util.Set.of(connection.first(), connection.second()))) continue;
                int firstIdx = index(carriages, connection.first());
                int secondIdx = index(carriages, connection.second());
                if(firstIdx < 0 || secondIdx < 0) continue;
                Carriage first = carriages.get(firstIdx);
                Carriage second = carriages.get(secondIdx);
                ScmAdaptiveStateModel firstModel = models.get(firstIdx);
                ScmAdaptiveStateModel secondModel = models.get(secondIdx);
                // Share the sampled response across every joint acting on the same section.
                int degree = Math.max(neighbours.get(connection.first()).size(), neighbours.get(connection.second()).size());
                // Remove relative swing while preserving the lead body's commanded rigid motion.
                Vec3 separation = second.center().subtract(first.center());
                Vec3 relativeVelocity = second.velocity().subtract(first.velocity())
                        .subtract(primary.angularVelocity().cross(separation));
                double reducedMass = first.mass() * second.mass() / (first.mass() + second.mass());
                double response = Math.min(2.0D, 0.25D / Math.max(1.0E-3D, tickSeconds));
                Vec3 damping = relativeVelocity.scale(response * reducedMass / degree);
                damping = supportedReaction(first, second, damping, false);
                forces.set(firstIdx, forces.get(firstIdx).add(damping));
                forces.set(secondIdx, forces.get(secondIdx).subtract(damping));
                // The complete inertia tensor damps yaw, pitch and roll without fixing joint angles.
                Vec3 relativeRotation = second.angularVelocity().subtract(first.angularVelocity());
                Tensor reducedInertia = first.inertia().inverse().add(second.inertia().inverse()).inverse();
                Vec3 torque = reducedInertia.transform(relativeRotation).scale(response / degree);
                torque = supportedReaction(first, second, torque, true);
                torques.set(firstIdx, torques.get(firstIdx).add(torque));
                torques.set(secondIdx, torques.get(secondIdx).subtract(torque));
            }
        }
        List<Request> res = new ArrayList<>();
        for(int idx = 0; idx < carriages.size(); idx++){
            res.add(new Request(carriages.get(idx).id(), forces.get(idx), torques.get(idx)));
        }
        return List.copyOf(res);
    }

    // Bound paired damping by opposing native wrench capacity before allocation.
    private static Vec3 supportedReaction(Carriage first, Carriage second, Vec3 requested, boolean angular){
        double magnitude = requested.length();
        if(magnitude <= 1.0E-12D) return Vec3.ZERO;
        Vec3 direction = requested.scale(1.0D / magnitude);
        Vec3 firstLimit = angular ? ScmPrecisionAllocator.physicalTorque(first.units(), direction)
                : ScmPrecisionAllocator.physicalForce(first.units(), direction);
        Vec3 secondLimit = angular ? ScmPrecisionAllocator.physicalTorque(second.units(), direction.scale(-1.0D))
                : ScmPrecisionAllocator.physicalForce(second.units(), direction.scale(-1.0D));
        double capacity = Math.min(Math.max(0.0D, firstLimit.dot(direction)),
                Math.max(0.0D, -secondLimit.dot(direction)));
        return direction.scale(Math.min(magnitude, capacity));
    }

    // Resolve a stable carriage identity in the supplied telemetry order
    private static int index(List<Carriage> carriages, UUID id){
        for(int idx = 0; idx < carriages.size(); idx++){
            if(carriages.get(idx).id().equals(id)) return idx;
        }
        return -1;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Retain the same cruise and precision selection for local and shared load allocation
    private static ScmPrecisionAllocator.Allocation allocate(List<ScmPrecisionAllocator.Unit> units,
            Vec3 force, Vec3 torque, Vec3 cruiseDirection, double[] previous){
        Vec3 direction = finite(cruiseDirection);
        return direction.lengthSqr() > 1.0E-12D
                ? ScmPrecisionAllocator.allocateCruise(units, force, torque, direction, previous)
                : ScmPrecisionAllocator.allocate(units, force, torque, previous);
    }

    // Derive local feedback from each carriage's live thrust and inertia
    private static ScmAdaptiveStateModel model(Carriage carriage, Vec3 holdingForce,
                                                double tickSeconds, double linearTolerance,
                                                double angularTolerance){
        double[] trim = trim(carriage, holdingForce);
        List<ScmAdaptiveStateModel.Actuator> actuators = new ArrayList<>();
        Tensor inverse = carriage.inertia().inverse();
        for(int idx = 0; idx < carriage.units().size(); idx++){
            ScmPrecisionAllocator.Unit unit = carriage.units().get(idx);
            actuators.addAll(ScmAdaptiveStateModel.forceActuators(unit, carriage.mass(), inverse, trim[idx]));
        }
        return ScmAdaptiveStateModel.sample(actuators, tickSeconds,
                linearTolerance, angularTolerance, characteristicLength(carriage));
    }

    // Find the local holding throttle without transferring lift through the couplers
    private static double[] trim(Carriage carriage, Vec3 holdingForce){
        return ScmPrecisionAllocator.allocate(
                carriage.units(), holdingForce, Vec3.ZERO, null).controls();
    }

    // Use local lever lengths so adding a carriage cannot tighten another carriage's poles
    private static double characteristicLength(Carriage carriage){
        double length = 1.0D;
        for(ScmPrecisionAllocator.Unit unit : carriage.units()){
            double force = unit.force().length();
            if(force > 1.0E-9D) length = Math.max(length, unit.torque().length() / force);
        }
        return length;
    }

    // Sum the current assembly mass
    private static double totalMass(List<Carriage> carriages){
        return carriages.stream().mapToDouble(Carriage::mass).sum();
    }

    // Keep unavailable vector components out of the control model
    private static Vec3 finite(Vec3 val){
        if(val == null) return Vec3.ZERO;
        return new Vec3(Double.isFinite(val.x) ? val.x : 0.0D,
                Double.isFinite(val.y) ? val.y : 0.0D,
                Double.isFinite(val.z) ? val.z : 0.0D);
    }
}
