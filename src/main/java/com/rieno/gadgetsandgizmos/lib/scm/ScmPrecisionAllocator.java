package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Arrays;

// Allocate physical force and torque while retaining small precision actuators
public final class ScmPrecisionAllocator{
    private static final int AXES = 6;
    private static final int ITERATIONS = 320;
    private static final int MAX_CACHED_ALLOCATIONS = 8;
    private static final int MAX_CACHED_UNITS = 16_384;
    private static final ThreadLocal<Map<AllocationKey, Allocation>> ALLOCATIONS =
            ThreadLocal.withInitial(() -> new LinkedHashMap<>(MAX_CACHED_ALLOCATIONS, 0.75F, true));
    private static final ThreadLocal<Workspace> WORKSPACES = ThreadLocal.withInitial(() -> new Workspace(0));
    private static final ThreadLocal<Map<List<Unit>, Geometry>> GEOMETRIES =
            ThreadLocal.withInitial(() -> new LinkedHashMap<>(16, 0.75F, true));

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
        Geometry geometry = geometry(available);
        double[] directed = solve(available, geometry, target, previous, false, false, allowed);
        double directedError = residual(geometry, directed, target);
        // Every unrestricted residual is nonnegative, so this directed result already wins
        if(directedError <= 0.03D) return new Allocation(directed, false, directedError);
        double[] all = solve(available, geometry, target, previous, false, false, null);
        double allError = residual(geometry, all, target);
        return directedError <= allError + 0.03D
                ? new Allocation(directed, false, directedError)
                : new Allocation(all, false, allError);
    }

    private static Allocation allocate(List<Unit> units, Vec3 force, Vec3 torque,
                                       double[] previous, boolean preferPrecision){
        List<Unit> available = units == null ? List.of() : List.copyOf(units);
        if(available.isEmpty()) return new Allocation(new double[0], false, 0.0D);
        double[] target = components(finite(force), finite(torque));
        Geometry geometry = geometry(available);
        if(preferPrecision && geometry.hasPrecision && canMeetPrecisionDemand(geometry, target)){
            double[] precise = solve(available, geometry, target, previous, true, true, null);
            double error = residual(geometry, precise, target);
            if(error <= 0.03D) return new Allocation(precise, true, error);
        }
        double[] all = solve(available, geometry, target, previous, false, preferPrecision, null);
        return new Allocation(all, false, residual(geometry, all, target));
    }

    // Reject a precision-only solve when an axis cannot reach the existing residual tolerance
    private static boolean canMeetPrecisionDemand(Geometry geometry, double[] target){
        double[] min = geometry.minimum, max = geometry.maximum, available = geometry.available;
        for(int axis = 0; axis < AXES; axis++){
            double scale = Math.max(1.0E-6D, Math.max(Math.abs(target[axis]), available[axis] * 0.02D));
            double tolerance = 0.03D * scale + 1.0E-10D * Math.max(scale, available[axis]);
            if(target[axis] < min[axis] - tolerance || target[axis] > max[axis] + tolerance) return false;
        }
        return true;
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
    private static double[] solve(List<Unit> units, Geometry geometry, double[] target,
                                  double[] previous, boolean precisionOnly,
                                  boolean preferPrecision, boolean[] allowed){
        int count = units.size();
        boolean hasPrecision = geometry.hasPrecision;
        double regularization = 1.0D / Math.max(1, geometry.poweredCount);
        Workspace work = WORKSPACES.get();
        if(work.control.length < count){
            work = new Workspace(count);
            if(count <= MAX_CACHED_UNITS) WORKSPACES.set(work);
        }
        double[][] columns = work.columns;
        double[] scale = work.scale;
        double[] control = work.control;
        boolean[] active = work.active;
        int[] activeIndices = work.activeIndices;
        Arrays.fill(scale, 0.0D);
        Arrays.fill(control, 0, count, 0.0D);
        Arrays.fill(active, 0, count, false);
        for(int idx = 0; idx < count; idx++) Arrays.fill(columns[idx], 0.0D);
        int activeCount = 0;
        for(int idx = 0; idx < count; idx++){
            Unit unit = units.get(idx);
            if(!geometry.powered[idx] || precisionOnly && !unit.precision()
                    || allowed != null && !allowed[idx]) continue;
            active[idx] = true;
            activeIndices[activeCount++] = idx;
            double[] column = columns[idx];
            System.arraycopy(geometry.columns[idx], 0, column, 0, AXES);
            control[idx] = previous != null && idx < previous.length
                    && (preferPrecision || !unit.precision())
                    ? component(previous[idx]) : 0.0D;
            for(int axis = 0; axis < AXES; axis++) scale[axis] += Math.abs(columns[idx][axis]);
        }
        for(int axis = 0; axis < AXES; axis++) scale[axis] = Math.max(1.0E-6D,
                Math.max(Math.abs(target[axis]), scale[axis] * 0.1D));
        for(int idx = 0; idx < count; idx++){
            double[] column = columns[idx];
            for(int axis = 0; axis < AXES; axis++){
                column[axis] /= scale[axis];
            }
        }
        double[] desired = work.desired;
        for(int axis = 0; axis < AXES; axis++) desired[axis] = target[axis] / scale[axis];
        double[] achieved = work.achieved;
        Arrays.fill(achieved, 0.0D);
        for(int idx = 0; idx < count; idx++){
            for(int axis = 0; axis < AXES; axis++){
                achieved[axis] += columns[idx][axis] * control[idx];
            }
        }
        // Reuse the fixed penalties and column magnitudes throughout the solve
        double[] penalties = work.penalties;
        double[] magnitudes = work.magnitudes;
        double[] prevControls = work.prevControls;
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
        double x = achieved[0], y = achieved[1], z = achieved[2];
        double rx = achieved[3], ry = achieved[4], rz = achieved[5];
        for(int iteration = 0; iteration < ITERATIONS; iteration++){
            double largestChange = 0.0D;
            for(int slot = 0; slot < activeCount; slot++){
                int idx = activeIndices[slot];
                double[] column = columns[idx];
                double gradient = penalties[idx]
                        * control[idx]
                        + 0.004D * regularization * (control[idx] - prevControls[idx]);
                gradient += column[0] * (x - desired[0]);
                gradient += column[1] * (y - desired[1]);
                gradient += column[2] * (z - desired[2]);
                gradient += column[3] * (rx - desired[3]);
                gradient += column[4] * (ry - desired[4]);
                gradient += column[5] * (rz - desired[5]);
                double next = Math.max(0.0D, Math.min(1.0D, control[idx] - gradient / magnitudes[idx]));
                double change = next - control[idx];
                if(change == 0.0D) continue;
                control[idx] = next;
                largestChange = Math.max(largestChange, Math.abs(change));
                x += column[0] * change;
                y += column[1] * change;
                z += column[2] * change;
                rx += column[3] * change;
                ry += column[4] * change;
                rz += column[5] * change;
            }
            if(largestChange <= 1.0E-8D) break;
        }
        return Arrays.copyOf(control, count);
    }

    // Reuse scratch arrays on one thread while returning detached allocation results
    private static final class Workspace{
        private final double[][] columns;
        private final double[] scale = new double[AXES];
        private final double[] desired = new double[AXES];
        private final double[] achieved = new double[AXES];
        private final double[] control;
        private final boolean[] active;
        private final int[] activeIndices;
        private final double[] penalties;
        private final double[] magnitudes;
        private final double[] prevControls;

        private Workspace(int count){
            columns = new double[count][AXES];
            control = new double[count];
            active = new boolean[count];
            activeIndices = new int[count];
            penalties = new double[count];
            magnitudes = new double[count];
            prevControls = new double[count];
        }
    }

    private static double residual(Geometry geometry, double[] controls, double[] target){
        double[] actual = new double[AXES];
        double[] available = geometry.available;
        for(int idx = 0; idx < geometry.columns.length; idx++){
            double[] column = geometry.columns[idx];
            for(int axis = 0; axis < AXES; axis++){
                actual[axis] += column[axis] * controls[idx];
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

    // Reuse only immutable full-output geometry; requests and previous controls remain live
    private static Geometry geometry(List<Unit> units){
        Map<List<Unit>, Geometry> cached = GEOMETRIES.get();
        Geometry res = cached.get(units);
        if(res != null) return res;
        res = new Geometry(units);
        if(units.size() <= MAX_CACHED_UNITS){
            int count = units.size();
            for(List<Unit> key : cached.keySet()) count += key.size();
            while(!cached.isEmpty() && (cached.size() >= 16 || count > MAX_CACHED_UNITS)){
                List<Unit> oldest = cached.keySet().iterator().next();
                count -= oldest.size();
                cached.remove(oldest);
            }
            cached.put(List.copyOf(units), res);
        }
        return res;
    }

    private static final class Geometry{
        private final double[][] columns;
        private final boolean[] powered;
        private final int poweredCount;
        private final boolean hasPrecision;
        private final double[] minimum = new double[AXES];
        private final double[] maximum = new double[AXES];
        private final double[] available = new double[AXES];

        private Geometry(List<Unit> units){
            columns = new double[units.size()][];
            powered = new boolean[units.size()];
            int count = 0;
            boolean precision = false;
            for(int idx = 0; idx < units.size(); idx++){
                Unit unit = units.get(idx);
                columns[idx] = components(unit.force(), unit.torque());
                powered[idx] = powered(unit);
                if(powered[idx]){
                    count++;
                    precision |= unit.precision();
                }
                for(int axis = 0; axis < AXES; axis++){
                    double val = columns[idx][axis];
                    available[axis] += Math.abs(val);
                    if(!unit.precision()) continue;
                    minimum[axis] += Math.min(0.0D, val);
                    maximum[axis] += Math.max(0.0D, val);
                }
            }
            poweredCount = count;
            hasPrecision = precision;
        }
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
