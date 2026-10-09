package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

// Allocate physical force and torque while retaining small precision actuators
public final class ScmPrecisionAllocator{
    private static final int AXES = 6;
    private static final int ITERATIONS = 320;
    private static final int MAX_CACHED_ALLOCATIONS = 8;
    private static final int MAX_CACHED_UNITS = 16_384;
    private static final ThreadLocal<Map<AllocationKey, Allocation>> ALLOCATIONS =
            ThreadLocal.withInitial(() -> new LinkedHashMap<>(MAX_CACHED_ALLOCATIONS, 0.75F, true));

    private ScmPrecisionAllocator(){}

    // Store the full-output force and torque of one independently controlled provider
    public record Unit(Vec3 force, Vec3 torque, boolean precision, Vec3 momentArm, double coneDegrees){
        public Unit{
            force = finite(force);
            torque = finite(torque);
            momentArm = finite(momentArm);
            coneDegrees = ScmThrustGeometry.coneDegrees(coneDegrees);
        }

        // Preserve fixed-direction provider construction
        public Unit(Vec3 force, Vec3 torque, boolean precision){ this(force, torque, precision, Vec3.ZERO, 0.0D); }
    }

    // Store the bounded control levels in the same order as the input units
    public record Allocation(double[] controls, boolean precisionOnly, double residual){
        public Allocation{
            controls = controls == null ? new double[0] : controls.clone();
        }

        @Override
        public double[] controls(){
            return controls.clone();
        }
    }

    // Convert a normalized six-axis request into physical force and torque
    public static Vec3 physicalForce(List<Unit> units, Vec3 demand){
        return physicalDemand(units, demand, false);
    }

    // Convert a correction while allowing lift-only providers to reduce existing support
    public static Vec3 physicalForce(List<Unit> units, Vec3 demand, Vec3 holdingForce){
        Vec3 target = finite(demand);
        Vec3 hold = finite(holdingForce);
        return new Vec3(component(target.x) * correctionCapacity(units, 0, target.x, hold.x),
                component(target.y) * correctionCapacity(units, 1, target.y, hold.y),
                component(target.z) * correctionCapacity(units, 2, target.z, hold.z));
    }

    // Convert a normalized six-axis request into physical force and torque
    public static Vec3 physicalTorque(List<Unit> units, Vec3 demand){
        return physicalDemand(units, demand, true);
    }

    // Normalize an acceleration request against current physical force capacity
    public static Vec3 normalizedAcceleration(List<Unit> units, Vec3 acceleration, double mass){
        return normalizedAcceleration(units, acceleration, mass, Vec3.ZERO);
    }

    // Preserve descent feedback when gravity is supported by upward thrust
    public static Vec3 normalizedAcceleration(List<Unit> units, Vec3 acceleration, double mass, Vec3 holdingForce){
        if(!Double.isFinite(mass) || mass <= 0.0D) return Vec3.ZERO;
        Vec3 force = finite(acceleration).scale(mass);
        Vec3 hold = finite(holdingForce);
        return new Vec3(normalized(force.x, correctionCapacity(units, 0, force.x, hold.x)),
                normalized(force.y, correctionCapacity(units, 1, force.y, hold.y)),
                normalized(force.z, correctionCapacity(units, 2, force.z, hold.z)));
    }

    // Express physical requests in the allocator's signed capacity units
    public static Vec3 normalizedForce(List<Unit> units, Vec3 force){
        return normalizedDemand(units, finite(force), 0);
    }

    public static Vec3 normalizedTorque(List<Unit> units, Vec3 torque){
        return normalizedDemand(units, finite(torque), 3);
    }

    private static Vec3 normalizedDemand(List<Unit> units, Vec3 val, int offset){
        return new Vec3(normalized(val.x, capacity(units, offset, val.x)),
                normalized(val.y, capacity(units, offset + 1, val.y)),
                normalized(val.z, capacity(units, offset + 2, val.z)));
    }

    // Use precision providers alone when they can produce the requested wrench
    public static Allocation allocate(List<Unit> units, Vec3 force, Vec3 torque,
                                      double[] previous){
        if(previous != null) return allocate(units, force, torque, previous, true);
        List<Unit> inputs = units == null ? List.of() : List.copyOf(units);
        AllocationKey key = new AllocationKey(inputs, finite(force), finite(torque));
        Map<AllocationKey, Allocation> allocations = ALLOCATIONS.get();
        Allocation res = allocations.get(key);
        if(res != null) return res;
        res = allocate(inputs, key.force(), key.torque(), null, true);
        if(inputs.size() <= MAX_CACHED_UNITS){
            int count = inputs.size();
            for(AllocationKey cached : allocations.keySet()) count += cached.units().size();
            while(!allocations.isEmpty()
                    && (allocations.size() >= MAX_CACHED_ALLOCATIONS || count > MAX_CACHED_UNITS)){
                AllocationKey oldest = allocations.keySet().iterator().next();
                count -= oldest.units().size();
                allocations.remove(oldest);
            }
            allocations.put(key, res);
        }
        return res;
    }

    // Cache holding allocations without retaining mutable throttle arrays or world state
    private record AllocationKey(List<Unit> units, Vec3 force, Vec3 torque){}

    // Favor main propulsion during sustained travel while retaining precision control
    public static Allocation allocateCruise(List<Unit> units, Vec3 force, Vec3 torque,
                                            double[] previous){
        return allocateCruise(units, force, torque, force, previous);
    }

    // Exclude engines opposing travel when the remaining providers can meet the wrench
    public static Allocation allocateCruise(List<Unit> units, Vec3 force, Vec3 torque,
                                            Vec3 travelDirection, double[] previous){
        List<Unit> available = units == null ? List.of() : List.copyOf(units);
        if(available.isEmpty()) return new Allocation(new double[0], false, 0.0D);
        Vec3 direction = finite(travelDirection);
        if(direction.lengthSqr() <= 1.0E-12D)
            return allocate(available, force, torque, previous, false);
        direction = direction.normalize();
        boolean[] allowed = new boolean[available.size()];
        boolean excluded = false;
        for(int idx = 0; idx < available.size(); idx++){
            Unit unit = available.get(idx);
            allowed[idx] = unit.precision() || unit.force().dot(direction)
                    >= -1.0E-6D * unit.force().length();
            excluded |= !allowed[idx];
        }
        if(!excluded) return allocate(available, force, torque, previous, false);
        double[] target = components(finite(force), finite(torque));
        double[] directed = solve(available, target, previous, false, false, allowed);
        double directedError = residual(available, directed, target);
        double[] all = solve(available, target, previous, false, false, null);
        double allError = residual(available, all, target);
        return directedError <= allError + 0.03D
                ? new Allocation(directed, false, directedError)
                : new Allocation(all, false, allError);
    }

    private static Allocation allocate(List<Unit> units, Vec3 force, Vec3 torque,
                                       double[] previous, boolean preferPrecision){
        List<Unit> available = units == null ? List.of() : List.copyOf(units);
        if(available.isEmpty()) return new Allocation(new double[0], false, 0.0D);
        double[] target = components(finite(force), finite(torque));
        boolean hasPrecision = available.stream().anyMatch(unit ->
                unit.precision() && powered(unit));
        if(preferPrecision && hasPrecision){
            double[] precise = solve(available, target, previous, true, true, null);
            double error = residual(available, precise, target);
            if(error <= 0.03D) return new Allocation(precise, true, error);
        }
        double[] all = solve(available, target, previous, false, preferPrecision, null);
        return new Allocation(all, false, residual(available, all, target));
    }

    private static Vec3 physicalDemand(List<Unit> units, Vec3 request, boolean torque){
        Vec3 target = finite(request);
        int offset = torque ? 3 : 0;
        return new Vec3(component(target.x) * capacity(units, offset, target.x),
                component(target.y) * capacity(units, offset + 1, target.y),
                component(target.z) * capacity(units, offset + 2, target.z));
    }

    private static double capacity(List<Unit> units, int axis, double request){
        if(units == null) return 0.0D;
        double result = 0.0D;
        for(Unit unit : units){
            if(unit == null) continue;
            if(unit.coneDegrees() > 0.0D && unit.force().lengthSqr() > 1.0E-12D){
                Vec3 direction = axis % 3 == 0 ? new Vec3(1, 0, 0)
                        : axis % 3 == 1 ? new Vec3(0, 1, 0) : new Vec3(0, 0, 1);
                if(request < 0.0D) direction = direction.scale(-1.0D);
                if(axis >= 3) direction = direction.cross(unit.momentArm());
                result += ScmThrustGeometry.maximumProjection(unit.force(), unit.coneDegrees(), direction);
                continue;
            }
            double value = axis < 3 ? component(unit.force(), axis)
                    : component(unit.torque(), axis - 3);
            result += request < 0.0D ? Math.max(0.0D, -value) : Math.max(0.0D, value);
        }
        return result;
    }

    // Reducing an opposing holding force is available even without reverse-facing propulsion
    private static double correctionCapacity(List<Unit> units, int axis, double request, double hold){
        return Math.max(capacity(units, axis, request), request * hold < 0.0D ? Math.abs(hold) : 0.0D);
    }

    // Refine bounded throttle levels without slowing every axis to the longest lever
    private static double[] solve(List<Unit> units, double[] target,
                                  double[] previous, boolean precisionOnly,
                                  boolean preferPrecision, boolean[] allowed){
        int count = units.size();
        boolean hasPrecision = units.stream().anyMatch(unit ->
                unit.precision() && powered(unit));
        double regularization = 1.0D / Math.max(1, units.stream().filter(ScmPrecisionAllocator::powered).count());
        double[][] columns = new double[count][AXES];
        double[] scale = new double[AXES];
        double[] control = new double[count];
        boolean[] active = new boolean[count];
        for(int idx = 0; idx < count; idx++){
            Unit unit = units.get(idx);
            if(!powered(unit) || precisionOnly && !unit.precision()
                    || allowed != null && !allowed[idx]) continue;
            active[idx] = true;
            columns[idx] = components(unit.force(), unit.torque());
            control[idx] = previous != null && idx < previous.length
                    && (preferPrecision || !unit.precision())
                    ? component(previous[idx]) : 0.0D;
            for(int axis = 0; axis < AXES; axis++) scale[axis] += Math.abs(columns[idx][axis]);
        }
        for(int axis = 0; axis < AXES; axis++) scale[axis] = Math.max(1.0E-6D,
                Math.max(Math.abs(target[axis]), scale[axis] * 0.1D));
        for(double[] column : columns){
            for(int axis = 0; axis < AXES; axis++){
                column[axis] /= scale[axis];
            }
        }
        double[] desired = new double[AXES];
        for(int axis = 0; axis < AXES; axis++) desired[axis] = target[axis] / scale[axis];
        double[] achieved = new double[AXES];
        for(int idx = 0; idx < count; idx++){
            for(int axis = 0; axis < AXES; axis++){
                achieved[axis] += columns[idx][axis] * control[idx];
            }
        }
        // Reuse the fixed penalties and column magnitudes throughout the solve
        double[] penalties = new double[count];
        double[] magnitudes = new double[count];
        double[] prevControls = new double[count];
        for(int idx = 0; idx < count; idx++){
            if(!active[idx]) continue;
            Unit unit = units.get(idx);
            prevControls[idx] = previous != null && idx < previous.length
                    && (preferPrecision || !unit.precision())
                    ? component(previous[idx]) : 0.0D;
            double penalty = !hasPrecision || unit.precision() == preferPrecision
                    ? 0.0002D : 0.015D;
            penalties[idx] = penalty * regularization;
            magnitudes[idx] = penalties[idx] + 0.004D * regularization;
            for(int axis = 0; axis < AXES; axis++){
                magnitudes[idx] += columns[idx][axis] * columns[idx][axis];
            }
        }
        for(int iteration = 0; iteration < ITERATIONS; iteration++){
            double largestChange = 0.0D;
            for(int idx = 0; idx < count; idx++){
                if(!active[idx]) continue;
                double gradient = penalties[idx]
                        * control[idx]
                        + 0.004D * regularization * (control[idx] - prevControls[idx]);
                for(int axis = 0; axis < AXES; axis++){
                    gradient += columns[idx][axis] * (achieved[axis] - desired[axis]);
                }
                double next = Math.max(0.0D, Math.min(1.0D, control[idx] - gradient / magnitudes[idx]));
                double change = next - control[idx];
                if(change == 0.0D) continue;
                control[idx] = next;
                largestChange = Math.max(largestChange, Math.abs(change));
                for(int axis = 0; axis < AXES; axis++) achieved[axis] += columns[idx][axis] * change;
            }
            if(largestChange <= 1.0E-8D) break;
        }
        return control;
    }

    private static double residual(List<Unit> units, double[] controls, double[] target){
        double[] actual = new double[AXES];
        double[] available = new double[AXES];
        for(int idx = 0; idx < units.size(); idx++){
            double[] column = components(units.get(idx).force(), units.get(idx).torque());
            for(int axis = 0; axis < AXES; axis++){
                actual[axis] += column[axis] * controls[idx];
                available[axis] += Math.abs(column[axis]);
            }
        }
        double largest = 0.0D;
        for(int axis = 0; axis < AXES; axis++){
            double scale = Math.max(1.0E-6D,
                    Math.max(Math.abs(target[axis]), available[axis] * 0.02D));
            largest = Math.max(largest, Math.abs(actual[axis] - target[axis]) / scale);
        }
        return largest;
    }

    private static double[] components(Vec3 force, Vec3 torque){
        return new double[]{force.x, force.y, force.z, torque.x, torque.y, torque.z};
    }

    private static boolean powered(Unit unit){
        return unit.force().lengthSqr() > 1.0E-12D
                || unit.torque().lengthSqr() > 1.0E-12D;
    }

    private static double component(Vec3 vector, int axis){
        return axis == 0 ? vector.x : axis == 1 ? vector.y : vector.z;
    }

    private static double component(double value){
        return Double.isFinite(value) ? Math.max(-1.0D, Math.min(1.0D, value)) : 0.0D;
    }

    private static double normalized(double force, double capacity){
        return capacity <= 1.0E-9D ? 0.0D : component(force / capacity);
    }

    private static Vec3 finite(Vec3 vector){
        if(vector == null) return Vec3.ZERO;
        return new Vec3(Double.isFinite(vector.x) ? vector.x : 0.0D,
                Double.isFinite(vector.y) ? vector.y : 0.0D,
                Double.isFinite(vector.z) ? vector.z : 0.0D);
    }
}
